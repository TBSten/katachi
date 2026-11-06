package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.dsl.KatachiInvalidTemplateParameterValueException
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.KatachiAmbiguousTemplateException
import me.tbsten.katachi.template.KatachiInvalidTemplateSetArgException
import me.tbsten.katachi.template.KatachiInvalidTemplateSetArgException.Problem
import me.tbsten.katachi.template.KatachiUnknownTemplateException
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.template.TemplateSetArgKind
import me.tbsten.katachi.template.TemplateSetDetail
import me.tbsten.katachi.template.internal.templateDescriptionJsonOf
import me.tbsten.katachi.template.internal.templateListLines
import me.tbsten.katachi.template.internal.templateSetDetailLines
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.dsl.files.fakeFileSystem

private fun emptyProject() = fakeFileSystem(workingDirectory = "/repo") { "/repo" { "gradlew"() } }

private fun Architecture.list(): TemplateList =
    process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow() as TemplateList

private fun Architecture.detail(specifier: String = "feature.NewFeature"): TemplateSetDetail =
    process(DescribeTemplates, DescribeTemplates.Args(template = specifier)).getOrThrow() as TemplateSetDetail

/** `DescribeTemplates` for template sets: the list, the detail, the JSON and the text. */
class TemplateSetDescribeSpec : FreeSpec({
    val homeSet: Architecture = templateSetArchitecture {
        title = "新しい画面"
        val name by stringParameter()
        val withState by booleanParameter(default = true)
        template("feature.FeatureScreen") { arg("name", name) }
        template("feature.FeatureViewModel") {
            arg("name", "${name}Main")
            arg("withState", withState)
            arg("size", 3)
        }
    }

    "一覧" - {
        "組が 0 個なら Template sets の見出しは出ない" {
            val arch = architectureOf { "a".group { "A".role { layout { "a" / "A.kt".file().template { "" } } } } }
            arch.list().templateSets shouldBe emptyList()
            templateListLines(arch.list()).none { it.startsWith("Template sets") } shouldBe true
        }

        "組は 1 個以上でテンプレートの後に並ぶ" {
            val lines = templateListLines(homeSet.list())
            val templatesAt = lines.indexOfFirst { it.endsWith("templates:") }
            val setsAt = lines.indexOfFirst { it.startsWith("Template sets (1)") }
            (templatesAt in 0 until setsAt) shouldBe true
            lines[setsAt + 1] shouldBe "  feature.NewFeature  新しい画面  --arg name --arg withState"
            lines[setsAt + 2] shouldBe "    members: feature.FeatureScreen, feature.FeatureViewModel"
        }

        "完全な指定・group 名・パラメータ名・メンバ（解いた後の指定）を運ぶ" {
            val summary = homeSet.list().templateSets.single()
            summary.templateSet shouldBe "feature.NewFeature"
            summary.name shouldBe "NewFeature"
            summary.title shouldBe "新しい画面"
            summary.groupName shouldBe "feature"
            summary.parameterNames shouldContainExactly listOf("name", "withState")
            summary.members shouldContainExactly listOf("feature.FeatureScreen", "feature.FeatureViewModel")
            summary.conflict shouldBe false
        }

        "メンバを group 抜きで書いても解いた後の完全な指定が並ぶ" {
            val arch = templateSetArchitecture {
                val name by stringParameter()
                template("FeatureScreen") { arg("name", name) }
            }
            arch.list().templateSets.single().members shouldContainExactly listOf("feature.FeatureScreen")
        }
    }

    "詳細" - {
        "組のパラメータは今のテンプレートと同じ規則でプレビューされる" {
            val detail = homeSet.detail()
            detail.parameters.map { it.name to it.previewValue } shouldContainExactly
                listOf("name" to "\${name}", "withState" to "true")
            detail.parameters.map { it.isRequired } shouldContainExactly listOf(true, false)
        }

        "メンバごとの arg と、組のパラメータそのままを渡した arg の parameter" {
            val members = homeSet.detail().members
            members.map { it.template } shouldContainExactly listOf("feature.FeatureScreen", "feature.FeatureViewModel")

            members[0].args.map { Triple(it.name, it.previewValue, it.parameter) } shouldContainExactly
                listOf(Triple("name", "\${name}", "name"))
            members[0].args.single().kind shouldBe TemplateSetArgKind.Capture

            // 式（`${name}Main`）と定数（3）は組のパラメータそのままではない。Boolean はそのまま渡している。
            members[1].args.map { Triple(it.name, it.previewValue, it.parameter) } shouldContainExactly listOf(
                Triple("name", "\${name}Main", null),
                Triple("withState", "true", "withState"),
                Triple("size", "3", null),
            )
            members[1].args.map { it.kind } shouldContainExactly
                listOf(TemplateSetArgKind.Capture, TemplateSetArgKind.Parameter, TemplateSetArgKind.Parameter)
        }

        "Boolean を反転して渡した arg は組のパラメータそのままとは言わない" {
            val arch = templateSetArchitecture {
                val withState by booleanParameter(default = true)
                template("feature.FeatureViewModel") {
                    arg("name", "X")
                    arg("withState", !withState)
                }
            }
            arch.detail().members.single().args.single { it.name == "withState" }.parameter shouldBe null
        }

        "組のパラメータと同じ値の定数を渡した arg は、組のパラメータそのままとは言わない" {
            val arch = templateSetArchitecture {
                val withState by booleanParameter(default = true)
                template("feature.FeatureViewModel") {
                    arg("name", "X")
                    arg("withState", true)
                }
                template("feature.Branchy") {
                    arg("name", "X")
                    arg("mode", withState.toString().let { BranchMode.Plain })
                }
            }
            val members = arch.detail().members
            members[0].args.single { it.name == "withState" }.parameter shouldBe null
            // withState は読まれているが、渡した値は withState から決まらない（常に Plain）。
            members[1].args.single { it.name == "mode" }.parameter shouldBe null
        }

        "Int と enum をそのまま渡した arg も parameter が付く" {
            val arch = templateSetArchitecture {
                val size by intParameter(default = 2)
                val mode by enumParameter(BranchMode.Plain)
                template("feature.Branchy") {
                    arg("name", "X")
                    arg("mode", mode)
                }
                template("feature.FeatureViewModel") {
                    arg("name", "X")
                    arg("size", size)
                }
            }
            val members = arch.detail().members
            members[0].args.single { it.name == "mode" }.parameter shouldBe "mode"
            members[1].args.single { it.name == "size" }.parameter shouldBe "size"
        }

        "パス: 素の capture は arg の値で埋め、渡されない capture はそのまま、pattern は宣言のまま" {
            val file = homeSet.detail().members[1].files.single()
            file.path shouldBe "feature/\${name}MainViewModel.kt"
            file.pattern shouldBe "feature/\${name}ViewModel.kt"
            file.captures shouldContainExactly listOf("name")
        }

        "arg の値が定数の capture はその値でパスが決まる" {
            val arch = templateSetArchitecture { template("feature.FeatureScreen") { arg("name", "Home") } }
            arch.detail().members.single().files.single().path shouldBe "feature/HomeScreen.kt"
        }

        "派生の capture は arg の値で埋めず <name.pascalCase> のまま、素の capture は埋める" {
            val arch = architectureOf {
                "feature".group {
                    "Derived".role {
                        layout {
                            "feature" / capture("name") / "${capture("name").pascalCase}Screen.kt".file().template { "" }
                        }
                    }
                    "NewFeature".templateSet { template("feature.Derived") { arg("name", "home") } }
                }
            }
            arch.detail().members.single().files.single().path shouldBe "feature/home/<name.pascalCase>Screen.kt"
        }

        "メンバの中身は arg の値で描かれる" {
            val content = homeSet.detail().members[1].files.single().content
            content shouldBe "vm \${name}Main state=true size=3"
        }

        "exampleCommand は capture へ渡る組のパラメータを <name> のスロットにする" {
            homeSet.detail().exampleCommand shouldBe
                "./gradlew katachiTemplate --arg template=feature.NewFeature --arg name=<name>"
        }

        "exampleCommand は capture へ渡らない必須の String を先頭大文字にする" {
            val arch = templateSetArchitecture {
                val name by stringParameter()
                val suffix by stringParameter()
                template("feature.FeatureTest") {
                    arg("name", name)
                    arg("suffix", suffix)
                }
            }
            arch.detail().exampleCommand shouldBe
                "./gradlew katachiTemplate --arg template=feature.NewFeature --arg name=<name> --arg suffix=Suffix"
        }

        "テキストはメンバごとの arg とファイルと Run it with を出す" {
            val text = templateSetDetailLines(homeSet.detail()).joinToString("\n")
            text shouldContain "Template set feature.NewFeature (新しい画面)"
            text shouldContain "member feature.FeatureViewModel"
            text shouldContain "  arg name <- \${name}Main"
            text shouldContain "feature/\${name}MainViewModel.kt"
            text shouldContain "Run it with"
        }

        "名前を group 抜きで指定しても組の詳細になる" {
            homeSet.detail("NewFeature").templateSet shouldBe "feature.NewFeature"
        }
    }

    "プレビューできない組" - {
        val unknownName = templateSetArchitecture { template("feature.FeatureScreen") { arg("typo", "x") } }
        val missing = templateSetArchitecture { template("feature.FeatureScreen") }
        val missingParameter = templateSetArchitecture {
            template("feature.FeatureTest") { arg("name", "X") }
            template("feature.FeatureScreen") { arg("name", "Y") }
        }

        "知らない名前を渡す組は一覧で conflict になり、詳細はその例外で落ちる" {
            unknownName.list().templateSets.single().conflict shouldBe true
            val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> { unknownName.detail() }
            thrown.problem shouldBe Problem.UnknownName
            thrown.names shouldContainExactly listOf("typo")
        }

        "capture に値を渡さない組は Missing で落ちる" {
            missing.list().templateSets.single().conflict shouldBe true
            val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> { missing.detail() }
            thrown.problem shouldBe Problem.Missing
            thrown.names shouldContainExactly listOf("name")
        }

        "既定値の無いパラメータをメンバに渡さない組も Missing で落ちる" {
            val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> { missingParameter.detail() }
            thrown.problem shouldBe Problem.Missing
            thrown.member shouldBe "feature.FeatureTest"
            thrown.names shouldContainExactly listOf("suffix")
        }

        "読めない値を渡す組は InvalidValue で落ち、メンバの行と元の例外を運ぶ" {
            val arch = templateSetArchitecture {
                template("feature.FeatureViewModel") {
                    arg("name", "X")
                    arg("withState", "maybe")
                }
            }
            arch.list().templateSets.single().conflict shouldBe true
            val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> { arch.detail() }
            thrown.problem shouldBe Problem.InvalidValue
            thrown.member shouldBe "feature.FeatureViewModel"
            thrown.names shouldContainExactly listOf("withState")
            thrown.values shouldBe mapOf("withState" to "maybe")
            thrown.declaredAt.fileName shouldBe "TemplateSetDescribeSpec.kt"
            (thrown.cause is KatachiInvalidTemplateParameterValueException) shouldBe true
        }

        "読めない値が分岐を決めるメンバでは InvalidValue が Missing より先に出る" {
            // name（capture）も渡していないが、先に言うのは読めない mode の方。
            val arch = templateSetArchitecture { template("feature.Branchy") { arg("mode", "bogus") } }
            val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> { arch.detail() }
            thrown.problem shouldBe Problem.InvalidValue
            thrown.names shouldContainExactly listOf("mode")
        }

        "メンバのブロックが落ちる組は一覧で conflict、詳細はその例外、JSON の詳細には出ない" {
            val arch = architectureOf {
                "feature".group {
                    "Boom".role {
                        layout { "feature" / "Boom.kt".file().template { error("boom") } }
                    }
                    "NewFeature".templateSet { template("feature.Boom") }
                }
            }
            arch.list().templateSets.single().conflict shouldBe true
            shouldThrow<IllegalStateException> { arch.detail() }.message shouldBe "boom"
            val json = templateDescriptionJsonOf(FakeArchitectureProcessContext(arch, DescribeTemplates.Args(), emptyProject()))
            json.templateSetDetails shouldBe emptyList()
        }

        "JSON の templateSetDetails には出ず、templateSets に conflict で残る" {
            val json = templateDescriptionJsonOf(FakeArchitectureProcessContext(unknownName, DescribeTemplates.Args(), emptyProject()))
            json.templateSets.single().conflict shouldBe true
            json.templateSetDetails shouldBe emptyList()
        }

        "壊れた組が 1 つあっても、他の組とテンプレートは一覧と JSON に出る" {
            val arch = brokenAndGood()
            val list = arch.list()
            list.templates.map { it.template } shouldContainExactly listOf("feature.FeatureScreen")
            list.templateSets.associate { it.name to it.conflict } shouldBe mapOf("Broken" to true, "Good" to false)
            val json = templateDescriptionJsonOf(FakeArchitectureProcessContext(arch, DescribeTemplates.Args(), emptyProject()))
            json.templateSetDetails.map { it.name } shouldContainExactly listOf("Good")
        }
    }

    "名前の解き方" - {
        "存在しない指定は Unknown で、宣言された組が一覧に載る" {
            val thrown = shouldThrow<KatachiUnknownTemplateException> { homeSet.detail("feature.Nope") }
            thrown.declaredTemplateSets shouldContainExactly listOf("feature.NewFeature")
        }

        "組とテンプレートの両方に当たる指定は Ambiguous" {
            val arch = architectureOf {
                "feature".group {
                    "Same".role { layout { "a" / "A.kt".file().template { "" } } }
                }
                "Same".templateSet { template("feature.Same") }
            }
            shouldThrow<KatachiAmbiguousTemplateException> { arch.detail("Same") }
        }
    }

})

/** `feature` with a set that cannot be previewed (`Broken`) and one that can (`Good`). */
private fun brokenAndGood(): Architecture =
    architectureOf {
        "feature".group {
            "FeatureScreen".role {
                layout { "feature" / "${capture("name")}Screen.kt".file().template { "screen ${captureValue("name")}" } }
            }
            "Broken".templateSet { template("feature.FeatureScreen") }
            "Good".templateSet {
                val name by stringParameter()
                template("feature.FeatureScreen") { arg("name", name) }
            }
        }
    }
