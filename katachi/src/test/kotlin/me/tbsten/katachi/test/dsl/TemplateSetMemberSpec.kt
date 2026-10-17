@file:Suppress("DEPRECATION")

package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.internal.assert
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.KatachiInvalidTemplateSetMemberException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateSetMemberException.Problem
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf

/** The members of a template set are resolved when the definition's layout is evaluated. */
class TemplateSetMemberSpec : FreeSpec({
    fun definition(members: List<String>): Architecture = architectureOf {
        "data".group {
            "Repository".role { layout { "repository" / "Repository.kt".file().template("repository") { "// repo" } } }
            "Cache".role { layout { "cache" / "Cache.kt".file().template { "// cache" } } }
        }
        "ui".group {
            "Cache".role { layout { "uiCache" / "UiCache.kt".file().template { "// ui cache" } } }
        }
        "Combo".templateSet { members.forEach { template(it) } }
    }

    "書いた指定がすべて当たる組は評価が通る" {
        shouldNotThrowAny { definition(listOf("data.Repository.repository", "data.Cache")).flattenLayout() }
    }

    "当たらない指定は flattenLayout で Unknown になり、候補に宣言されたテンプレートが並ぶ" {
        val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> { definition(listOf("data.Missing")).flattenLayout() }

        thrown.problem shouldBe Problem.Unknown
        thrown.templateSet shouldBe "Combo"
        thrown.member shouldBe "data.Missing"
        thrown.candidates shouldContainExactly listOf("data.Cache", "data.Repository.repository", "ui.Cache")
        thrown.message!! shouldContain "data.Missing"
    }

    "group を省いた指定が 2 つの Role に当たると Ambiguous、完全な指定なら通る" {
        val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> { definition(listOf("Cache")).flattenLayout() }

        thrown.problem shouldBe Problem.Ambiguous
        thrown.candidates shouldContainExactly listOf("data.Cache", "ui.Cache")

        shouldNotThrowAny { definition(listOf("ui.Cache")).flattenLayout() }
    }

    "同じテンプレートを別の綴りで 2 回書くと Duplicate" {
        val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> {
            definition(listOf("data.Repository.repository", "Repository.repository")).flattenLayout()
        }

        thrown.problem shouldBe Problem.Duplicate
        thrown.member shouldBe "Repository.repository"
    }

    "メンバに別の組の指定は書けず Unknown になり、候補にも組を出さない" {
        val arch = architecture {
            "Role".role { layout { "role" / "Role.kt".file().template { "// role" } } }
            "Inner".templateSet { template("Role") }
            "Outer".templateSet { template("Inner") }
        }

        val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> { arch.flattenLayout() }

        thrown.templateSet shouldBe "Outer"
        thrown.problem shouldBe Problem.Unknown
        thrown.candidates shouldContainExactly listOf("Role")
    }

    "宣言の行がメンバの template(...) の行を指す" {
        var line = 0
        val arch = architecture {
            "Role".role { layout { "role" / "Role.kt".file().template { "// role" } } }
            "Combo".templateSet {
                template("Role")
                line = Throwable().stackTrace.first().lineNumber + 1
                template("nope")
            }
        }

        val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> { arch.flattenLayout() }

        thrown.declaredAt.fileName shouldBe "TemplateSetMemberSpec.kt"
        thrown.declaredAt.lineNumber shouldBe line
    }

    "Duplicate は 2 つ目のメンバの行を指し、1 つ目の行と当たったテンプレートを持つ" {
        var first = 0
        var second = 0
        val arch = architecture {
            "Repository".role { layout { "repository" / "Repository.kt".file().template { "// repo" } } }
            "Combo".templateSet {
                first = Throwable().stackTrace.first().lineNumber + 1
                template("Repository")
                second = Throwable().stackTrace.first().lineNumber + 1
                template("Repository")
            }
        }

        val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> { arch.flattenLayout() }

        thrown.problem shouldBe Problem.Duplicate
        thrown.declaredAt.lineNumber shouldBe second
        thrown.firstDeclaredAt!!.lineNumber shouldBe first
        thrown.candidates shouldContainExactly listOf("Repository")
        thrown.message!! shouldContain "names \"Repository\""
    }

    "別の綴りでの Duplicate の文面は、書いた綴りと当たったテンプレートの両方を出す" {
        val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> {
            definition(listOf("data.Repository.repository", "Repository.repository")).flattenLayout()
        }

        thrown.message!! shouldContain "member \"Repository.repository\""
        thrown.message!! shouldContain "names \"data.Repository.repository\""
    }

    "2 つの組が同じテンプレートをメンバに持っても評価が通る" {
        val arch = architecture {
            "Repository".role { layout { "repository" / "Repository.kt".file().template { "// repo" } } }
            "First".templateSet { template("Repository") }
            "Second".templateSet { template("Repository") }
        }

        shouldNotThrowAny { arch.flattenLayout() }
    }

    "3 つの Problem の文面は英語の ASCII だけで、宣言位置を含み、最後の行が直し方になる" {
        val thrown = listOf(
            shouldThrow<KatachiInvalidTemplateSetMemberException> { definition(listOf("data.Missing")).flattenLayout() },
            shouldThrow<KatachiInvalidTemplateSetMemberException> { definition(listOf("Cache")).flattenLayout() },
            shouldThrow<KatachiInvalidTemplateSetMemberException> {
                definition(listOf("data.Cache", "data.Cache")).flattenLayout()
            },
        )

        thrown.map { it.problem } shouldContainExactly Problem.entries.toList()
        for (each in thrown) {
            val message = each.message.orEmpty()

            message.all { it.code in 0x20..0x7e || it == '\n' } shouldBe true
            message shouldContain "TemplateSetMemberSpec.kt:"
            message shouldContain "\"Combo\""
            message.lines().size shouldBe 3
            message.lines().last().isNotBlank() shouldBe true
            message shouldNotContain "null"
        }
    }

    "モジュールの capture の下のテンプレートをメンバにした組" - {
        fun moduleDefinition(member: String): Architecture = architectureOf {
            "feature".group {
                "Screen".role {
                    layout {
                        ":feature:${capture("feature")}".module {
                            "Screen.kt".file().template { "// screen" }
                        }
                    }
                }
            }
            "NewFeature".templateSet { template(member) }
        }
        val noModules = repositoryOf { }

        "feature モジュールが 0 個のプロジェクトでも assert() が通る" {
            shouldNotThrowAny { moduleDefinition("feature.Screen").assert(noModules) }
        }

        "feature モジュールが 0 個のプロジェクトでも declaredEntries を読める" {
            shouldNotThrowAny {
                moduleDefinition("feature.Screen").process(noModules) { context -> context.declaredEntries }
            }
        }

        "同じ定義で当たらないメンバは assert() でも Unknown になる" {
            val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> {
                moduleDefinition("feature.Missing").assert(noModules)
            }

            thrown.problem shouldBe Problem.Unknown
            thrown.candidates shouldContainExactly listOf("feature.Screen")
        }
    }

    "当たらない指定は assert() でも declaredEntries でも Unknown になる" - {
        val tree = repositoryOf { }

        "assert()" {
            val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> {
                definition(listOf("data.Missing")).assert(tree)
            }

            thrown.problem shouldBe Problem.Unknown
            thrown.member shouldBe "data.Missing"
        }

        "declaredEntries" {
            val thrown = shouldThrow<KatachiInvalidTemplateSetMemberException> {
                definition(listOf("data.Missing")).process(tree) { context -> context.declaredEntries }
            }

            thrown.problem shouldBe Problem.Unknown
            thrown.member shouldBe "data.Missing"
        }
    }
})
