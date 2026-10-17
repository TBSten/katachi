@file:Suppress("DEPRECATION")

package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly as shouldContainExactlyMap
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.DeclarationKind
import me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException
import me.tbsten.katachi.dsl.KatachiInvalidIdentifierException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateSetException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateSetException.Problem
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.internal.TemplateSetMode
import me.tbsten.katachi.dsl.internal.replayTemplateSet

class TemplateSetDslSpec : FreeSpec({
    "宣言" - {
        "直下と group の中と gradle の中に書いた組が、直下、group の順で allTemplateSets に入る" {
            val arch = architecture {
                "feature".group {
                    "NewFeature".templateSet { template("feature.A") }
                }
                "Root".templateSet { template("A") }
                gradle {
                    "FromGradle".templateSet { template("A") }
                }
            }

            // gradle { } declares into its own "Gradle" group, which comes after the groups written before it.
            arch.allTemplateSets.map { it.specifier } shouldContainExactly
                listOf("Root", "feature.NewFeature", "Gradle.FromGradle")
        }

        "完全な指定は group の中なら group の修飾名つき、直下なら名前だけ" {
            val arch = architecture {
                "feature".group {
                    "model".group { "Deep".templateSet { template("A") } }
                    "NewFeature".templateSet { template("A") }
                }
                "Root".templateSet { template("A") }
            }

            arch.allTemplateSets.map { it.specifier } shouldContainExactly
                listOf("Root", "feature.NewFeature", "feature.model.Deep")
        }

        "外側のブロックは宣言の時点で 1 回だけ走り、template の内側は走らない" {
            var outer = 0
            var inner = 0

            architecture {
                "Set".templateSet {
                    outer++
                    template("A") { inner++ }
                    template("B") { inner++ }
                }
            }

            outer shouldBe 1
            inner shouldBe 0
        }

        "title と summary とパラメータとメンバの並びを宣言が持つ" {
            val arch = architecture {
                "Set".templateSet {
                    title = "A set"
                    summary = "Generates two files"
                    val name by stringParameter()
                    val withTest by booleanParameter(default = true)
                    template("a.B") { arg("name", name) }
                    template("a.C") { arg("withTest", withTest) }
                }
            }

            val set = arch.allTemplateSets.single()
            set.title shouldBe "A set"
            set.summary shouldBe "Generates two files"
            set.parameters.map { it.name } shouldContainExactly listOf("name", "withTest")
            set.parameters.map { it.default } shouldContainExactly listOf(null, true)
            set.members.map { it.specifier } shouldContainExactly listOf("a.B", "a.C")
        }

        "template の block を省いたメンバも宣言できる" {
            val arch = architecture { "Set".templateSet { template("a.B") } }

            arch.allTemplateSets.single().members.map { it.specifier } shouldContainExactly listOf("a.B")
        }
    }

    "名前の規則と重複" - {
        "識別子でない名前は DeclarationKind.TemplateSet の KatachiInvalidIdentifierException になる" {
            val thrown = shouldThrow<KatachiInvalidIdentifierException> {
                architecture { "new feature".templateSet { template("A") } }
            }

            thrown.kind shouldBe DeclarationKind.TemplateSet
            thrown.name shouldBe "new feature"
            thrown.message shouldContain "Invalid template set name"
        }

        "group と role と templateSet の同名は、種類の組み合わせ 3x3 の全部で重複になる" {
            val kinds = listOf(DeclarationKind.Group, DeclarationKind.Role, DeclarationKind.TemplateSet)

            for (first in kinds) for (second in kinds) {
                val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
                    architecture {
                        "x".group {
                            declareDup(first)
                            declareDup(second)
                        }
                    }
                }

                thrown.name shouldBe "dup"
                thrown.firstKind shouldBe first
                thrown.kind shouldBe second
                thrown.scope shouldBe "group \"x\""
            }
        }

        "種類が違う重複の文面に、先の種類と後の種類の両方の label が出る" {
            val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
                architecture {
                    "dup".role { }
                    "dup".templateSet { template("A") }
                }
            }

            thrown.message shouldContain "A role of that name was already declared"
            thrown.message shouldContain "A role and a template set in the root of architecture { } " +
                "would both be referred to as \"dup\". Rename one of them."
        }

        "同じ種類の templateSet の重複は templateSet の一意性の規則を言う" {
            val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
                architecture {
                    "dup".templateSet { template("A") }
                    "dup".templateSet { template("A") }
                }
            }

            thrown.message shouldContain "Template set names must be unique"
        }

        "別の group でなら同じ名前の templateSet を宣言できる" {
            val arch = architecture {
                "a".group { "Set".templateSet { template("X") } }
                "b".group { "Set".templateSet { template("X") } }
            }

            arch.allTemplateSets.map { it.specifier } shouldContainExactly listOf("a.Set", "b.Set")
        }
    }

    "宣言の誤り" - {
        "外側で値を読むと行番号つきの ValueReadOutsideMember になる" {
            var line = 0
            val thrown = shouldThrow<KatachiInvalidTemplateSetException> {
                architecture {
                    "feature".group {
                        "NewFeature".templateSet {
                            // Kotlin attributes a read of a local delegated property to the line of its
                            // declaration, so that is the line the exception can point at.
                            val withTest by booleanParameter(default = true); line = Throwable().stackTrace.first().lineNumber
                            if (withTest) template("a.Test")
                            template("a.Screen")
                        }
                    }
                }
            }

            thrown.problem shouldBe Problem.ValueReadOutsideMember
            thrown.templateSet shouldBe "feature.NewFeature"
            thrown.name shouldBe "withTest"
            thrown.declaredAt.fileName shouldBe "TemplateSetDslSpec.kt"
            thrown.declaredAt.lineNumber shouldBe line
        }

        "パラメータの既定値に別のパラメータの値を使っても ValueReadOutsideMember になる" {
            val thrown = shouldThrow<KatachiInvalidTemplateSetException> {
                architecture {
                    "Set".templateSet {
                        val name by stringParameter()
                        val alias by stringParameter(default = "${name}Alias")
                        template("a.B") { arg("alias", alias) }
                    }
                }
            }

            thrown.problem shouldBe Problem.ValueReadOutsideMember
            thrown.name shouldBe "name"
        }

        "メンバが 0 個だと Empty になり、組の宣言の行を指す" {
            val thrown = shouldThrow<KatachiInvalidTemplateSetException> {
                architecture { "Set".templateSet { title = "none" } }
            }

            thrown.problem shouldBe Problem.Empty
            thrown.declaredAt.fileName shouldBe "TemplateSetDslSpec.kt"
        }

        "入れ子のスコープで同じ名前のパラメータを宣言すると 2 つの行を持つ DuplicateParameter になる" {
            val thrown = shouldThrow<KatachiInvalidTemplateSetException> {
                architecture {
                    "Set".templateSet {
                        run {
                            val name by stringParameter()
                            template("a.B") { arg("name", name) }
                        }
                        run {
                            val name by stringParameter()
                            template("a.C") { arg("name", name) }
                        }
                    }
                }
            }

            thrown.problem shouldBe Problem.DuplicateParameter
            thrown.name shouldBe "name"
            (thrown.firstDeclaredAt?.lineNumber ?: 0) shouldBe (thrown.declaredAt.lineNumber - 4)
        }

        "by を書き忘れたパラメータは UnboundParameter になる" {
            val thrown = shouldThrow<KatachiInvalidTemplateSetException> {
                architecture {
                    "Set".templateSet {
                        @Suppress("UNUSED_VARIABLE")
                        val name = stringParameter()
                        template("a.B")
                    }
                }
            }

            thrown.problem shouldBe Problem.UnboundParameter
        }

        "template と onExisting という名前のパラメータは ReservedParameterName になる" {
            val byTemplate = shouldThrow<KatachiInvalidTemplateSetException> {
                architecture {
                    "Set".templateSet {
                        val template by stringParameter()
                        template("a.B") { arg("x", template) }
                    }
                }
            }
            val byOnExisting = shouldThrow<KatachiInvalidTemplateSetException> {
                architecture {
                    "Set".templateSet {
                        val onExisting by stringParameter()
                        template("a.B") { arg("x", onExisting) }
                    }
                }
            }

            byTemplate.problem shouldBe Problem.ReservedParameterName
            byTemplate.name shouldBe "template"
            byOnExisting.problem shouldBe Problem.ReservedParameterName
            byOnExisting.name shouldBe "onExisting"
        }
    }

    "Run と Preview の replay" - {
        fun declared() = architecture {
            "feature".group {
                "NewFeature".templateSet {
                    val feature by stringParameter()
                    val name by stringParameter()
                    val withState by booleanParameter(default = true)
                    template("a.Screen") {
                        arg("feature", feature)
                        arg("name", name)
                    }
                    template("a.ViewModel") {
                        arg("feature", feature)
                        arg("name", "${name}Main")
                        arg("withState", false)
                        arg("count", 3)
                        arg("kind", Problem.Empty)
                        arg("again", withState)
                    }
                }
            }
        }.allTemplateSets.single()

        "メンバごとに別の値を渡せ、型ごとの綴りで arg の表に入る" {
            val replay = replayTemplateSet(declared(), TemplateSetMode.Run(mapOf("feature" to "home", "name" to "Home")))

            replay.members.map { it.specifier } shouldContainExactly listOf("a.Screen", "a.ViewModel")
            replay.members[0].args shouldContainExactlyMap mapOf("feature" to "home", "name" to "Home")
            replay.members[1].args shouldContainExactlyMap mapOf(
                "feature" to "home",
                "name" to "HomeMain",
                "withState" to "false",
                "count" to "3",
                "kind" to "Empty",
                "again" to "true",
            )
            replay.missing shouldContainExactly emptyList<String>()
        }

        "値が無いパラメータは missing に集まり、読めない値は invalid に集まる" {
            val replay = replayTemplateSet(declared(), TemplateSetMode.Run(mapOf("withState" to "maybe")))

            replay.missing shouldContainExactly listOf("feature", "name")
            replay.invalid.keys shouldContainExactly listOf("withState")
        }

        "Preview の replay でも同じメンバが並ぶ" {
            val replay = replayTemplateSet(declared(), TemplateSetMode.Preview(mapOf("feature" to "f", "name" to "n")))

            replay.members.map { it.specifier } shouldContainExactly listOf("a.Screen", "a.ViewModel")
        }

        "isPreview は Preview のメンバの中だけ true になる" {
            val seen = mutableListOf<Boolean>()
            val set = architecture { "Set".templateSet { template("a.B") { seen += isPreview } } }
                .allTemplateSets.single()

            replayTemplateSet(set, TemplateSetMode.Run(emptyMap()))
            replayTemplateSet(set, TemplateSetMode.Preview(emptyMap()))

            seen shouldContainExactly listOf(false, true)
        }

        "同じ arg の名前を 1 つのメンバで 2 回書くと DuplicateArg になり、2 回目の行を指す" {
            val set = architecture {
                "Set".templateSet {
                    template("a.B") {
                        arg("name", "x")
                        arg("name", "y")
                    }
                }
            }.allTemplateSets.single()

            val thrown = shouldThrow<KatachiInvalidTemplateSetException> {
                replayTemplateSet(set, TemplateSetMode.Run(emptyMap()))
            }

            thrown.problem shouldBe Problem.DuplicateArg
            thrown.name shouldBe "name"
            (thrown.declaredAt.lineNumber - (thrown.firstDeclaredAt?.lineNumber ?: 0)) shouldBe 1
        }

        "宣言のときと違うメンバを書く replay は MembersChanged になる" {
            var includeSecond = true
            val set = architecture {
                "Set".templateSet {
                    template("a.B")
                    if (includeSecond) template("a.C")
                }
            }.allTemplateSets.single()
            includeSecond = false

            val thrown = shouldThrow<KatachiInvalidTemplateSetException> {
                replayTemplateSet(set, TemplateSetMode.Run(emptyMap()))
            }

            thrown.problem shouldBe Problem.MembersChanged
        }

        "メンバの外で値を読むブロックは Run でも ValueReadOutsideMember になる" {
            var readOutside = false
            val set = architecture {
                "Set".templateSet {
                    val name by stringParameter(default = "x")
                    if (readOutside) name.length
                    template("a.B") { arg("name", name) }
                }
            }.allTemplateSets.single()
            readOutside = true

            val thrown = shouldThrow<KatachiInvalidTemplateSetException> {
                replayTemplateSet(set, TemplateSetMode.Run(emptyMap()))
            }

            thrown.problem shouldBe Problem.ValueReadOutsideMember
        }
    }

    "例外の文面" - {
        fun every(): Map<Problem, KatachiInvalidTemplateSetException> {
            fun catching(block: () -> Unit) = shouldThrow<KatachiInvalidTemplateSetException> { block() }
            var members = true
            val changing = architecture {
                "Set".templateSet {
                    template("a.B")
                    if (members) template("a.C")
                }
            }.allTemplateSets.single()
            members = false
            val dupArg = architecture {
                "Set".templateSet { template("a.B") { arg("n", "1"); arg("n", "2") } }
            }.allTemplateSets.single()
            return listOf(
                catching { architecture { "Set".templateSet { } } },
                catching {
                    architecture {
                        "Set".templateSet {
                            val w by booleanParameter(default = true)
                            if (w) template("a.B") else template("a.C")
                        }
                    }
                },
                catching {
                    architecture {
                        "Set".templateSet {
                            run { val n by stringParameter(); template("a.B") { arg("n", n) } }
                            run { val n by stringParameter(); template("a.C") { arg("n", n) } }
                        }
                    }
                },
                catching {
                    architecture {
                        @Suppress("UNUSED_VARIABLE")
                        "Set".templateSet { val n = stringParameter(); template("a.B") }
                    }
                },
                catching {
                    architecture {
                        "Set".templateSet { val onExisting by stringParameter(); template("a.B") { arg("x", onExisting) } }
                    }
                },
                catching { replayTemplateSet(dupArg, TemplateSetMode.Run(emptyMap())) },
                catching { replayTemplateSet(changing, TemplateSetMode.Run(emptyMap())) },
            ).associateBy { it.problem }
        }

        "7 つの Problem すべてが揃っている" {
            every().keys.toList() shouldContainExactly Problem.entries.toList()
        }

        "どの文面も英語の ASCII だけで、宣言位置を含み、最後の行が直し方になる" {
            for ((problem, thrown) in every()) {
                val message = thrown.message.orEmpty()

                message.all { it.code in 0x20..0x7e || it == '\n' } shouldBe true
                message shouldContain "TemplateSetDslSpec.kt:"
                message shouldContain "\"Set\""
                message.lines().size shouldBe 3
                message.lines().last().isNotBlank() shouldBe true
                message shouldNotContain "null"
                problem shouldBe thrown.problem
            }
        }
    }
})

/** Declares "dup" as a [kind], whichever of the three declarations that is. */
private fun me.tbsten.katachi.dsl.GroupScope.declareDup(kind: DeclarationKind) {
    when (kind) {
        DeclarationKind.Group -> "dup".group { }
        DeclarationKind.Role -> "dup".role { }
        else -> "dup".templateSet { template("A") }
    }
}
