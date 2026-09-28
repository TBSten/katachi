package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.KatachiTemplateParameterConflictException
import me.tbsten.katachi.dsl.TemplateScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.DescribeTemplatesFormat
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

private enum class Mode { Plain, Fancy }

/** A ViewModel role whose feature level is named [captureName], with [block] as its template. */
private fun conflicting(captureName: String = "feature", block: TemplateScope.() -> Unit): Architecture =
    architecture {
        "feature".group {
            "ViewModel" {
                layout { "feature" / capture(captureName) / "*ViewModel.kt".file() }
                template(block)
            }
        }
    }

private fun Architecture.describe(roleName: String?) = DescribeTemplates.process(
    FakeArchitectureProcessContext(
        architecture = this,
        args = DescribeTemplates.Args(roleName = roleName),
        fileSystem = ForbiddenFileSystem,
    ),
).getOrThrow()

private fun Architecture.undeclaredArgNames(values: Map<String, String>) = GenerateCodeFromTemplate.undeclaredArgNames(
    FakeArchitectureProcessContext(architecture = this, args = Unit, fileSystem = ForbiddenFileSystem, rawArgs = values),
)

/**
 * A capture and another input of the template answering to one `--arg` name.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.template` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class TemplateCaptureConflictSpec : FreeSpec({
    "capture の名前が stringParameter の名前と同じだと KatachiTemplateParameterConflictException" {
        val arch = conflicting {
            val feature by stringParameter()
            file("${feature}ViewModel.kt") { "" }
        }

        val thrown = shouldThrow<KatachiTemplateParameterConflictException> {
            arch.generated("ViewModel", mapOf("feature" to "home"))
        }
        thrown.name shouldBe "feature"
        thrown.conflictsWith shouldContain "stringParameter()"
        thrown.captureDeclaredAt.fileName shouldBe "TemplateCaptureConflictSpec.kt"
        thrown.parameterDeclaredAt?.fileName shouldBe "TemplateCaptureConflictSpec.kt"
        thrown.message.orEmpty() shouldContain "captureValue(\"feature\")"
        withClue("declared を2回言わない") {
            thrown.message.orEmpty() shouldContain "declared with stringParameter() at TemplateCaptureConflictSpec.kt:"
            thrown.message.orEmpty() shouldNotContain "declared at TemplateCaptureConflictSpec.kt:${thrown.parameterDeclaredAt?.lineNumber}."
        }
    }

    "booleanParameter / intParameter / enumParameter の名前とも同じく落ちる" - {
        listOf<Pair<String, TemplateScope.() -> Unit>>(
            "booleanParameter()" to {
                val feature by booleanParameter(default = true)
                file("${feature}ViewModel.kt") { "" }
            },
            "intParameter()" to {
                val feature by intParameter(default = 1)
                file("V${feature}ViewModel.kt") { "" }
            },
            "enumParameter()" to {
                val feature by enumParameter(default = Mode.Plain)
                file("${feature}ViewModel.kt") { "" }
            },
        ).forEach { (declaredWith, template) ->
            declaredWith {
                shouldThrow<KatachiTemplateParameterConflictException> {
                    conflicting(block = template).generated("ViewModel", mapOf("feature" to "home"))
                }.conflictsWith shouldContain declaredWith
            }
        }
    }

    "capture の名前が roleName / onExisting だと KatachiTemplateParameterConflictException" - {
        listOf("roleName", "onExisting").forEach { name ->
            name {
                val thrown = shouldThrow<KatachiTemplateParameterConflictException> {
                    conflicting(captureName = name) { file("HomeViewModel.kt") { "" } }.generated("ViewModel")
                }
                thrown.conflictsWith shouldBe "GenerateCodeFromTemplate.Args.$name"
                thrown.parameterDeclaredAt.shouldBeNull()
                withClue("processor の引数は改名できないので、capture の改名だけを案内する") {
                    thrown.message.orEmpty() shouldContain "Rename the capture: $name is an argument of the template processor itself"
                    thrown.message.orEmpty() shouldNotContain "or the parameter"
                }
            }
        }
    }

    "モジュールの capture の名前でも落ちる" {
        val arch = architecture {
            "Screen" {
                layout { ":feature:*".module(capture = "feature") { "*Screen.kt".file() } }
                template {
                    val feature by stringParameter()
                    file("${feature}Screen.kt") { "" }
                }
            }
        }

        shouldThrow<KatachiTemplateParameterConflictException> {
            arch.undeclaredArgNames(mapOf("roleName" to "Screen"))
        }.name shouldBe "feature"
    }

    "分岐の中でだけ宣言されたパラメータとの衝突は、その分岐を通らない実行でも落ちる" {
        val arch = conflicting {
            val withFeature by booleanParameter(default = false)
            if (withFeature) {
                val feature by stringParameter()
                file("${feature}ViewModel.kt") { "" }
            } else {
                file("HomeViewModel.kt") { "" }
            }
        }

        shouldThrow<KatachiTemplateParameterConflictException> {
            arch.generated("ViewModel", mapOf("feature" to "home"))
        }.name shouldBe "feature"
        shouldThrow<KatachiTemplateParameterConflictException> {
            arch.generated("ViewModel", mapOf("feature" to "home", "withFeature" to "true"))
        }
        shouldThrow<KatachiTemplateParameterConflictException> {
            arch.undeclaredArgNames(mapOf("roleName" to "ViewModel", "feature" to "home"))
        }
    }

    "2つの分岐を重ねた内側で宣言されたパラメータとの衝突も落ちる" {
        val arch = conflicting {
            val mode by enumParameter(default = Mode.Plain)
            val withFeature by booleanParameter(default = false)
            if (mode == Mode.Fancy && withFeature) {
                val feature by stringParameter()
                file("${feature}ViewModel.kt") { "" }
            } else {
                file("HomeViewModel.kt") { "" }
            }
        }

        shouldThrow<KatachiTemplateParameterConflictException> {
            arch.generated("ViewModel", mapOf("feature" to "home"))
        }.name shouldBe "feature"
    }

    "衝突の無いテンプレートは、分岐を探っても今までどおり生成できる" {
        val arch = conflicting {
            val withTest by booleanParameter(default = false)
            val name by stringParameter()
            file("${name}ViewModel.kt") { "" }
            if (withTest) {
                val testName by stringParameter(default = "Test")
                file("${name}ViewModel$testName.kt") { "" }
            }
        }

        arch.generatedPaths("ViewModel", mapOf("feature" to "home", "name" to "Home")) shouldBe
            listOf("feature/home/HomeViewModel.kt")
    }

    "衝突した役割は DescribeTemplates の一覧を落とさず、fileCount が null になる" {
        val arch = architecture {
            "feature".group {
                "ViewModel" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    template {
                        val feature by stringParameter()
                        file("${feature}ViewModel.kt") { "" }
                    }
                }
                "Screen" {
                    layout { "feature" / capture("feature") / "*Screen.kt".file() }
                    template { file("HomeScreen.kt") { "" } }
                }
            }
        }

        val list = arch.describe(roleName = null).shouldBeInstanceOf<TemplateList>()
        list.templates.map { it.roleName } shouldContainExactly listOf("feature/ViewModel", "feature/Screen")
        list.templates.single { it.roleName == "feature/ViewModel" }.fileCount.shouldBeNull()
        list.templates.single { it.roleName == "feature/Screen" }.fileCount shouldBe 1
    }

    "衝突した役割があっても format=json は他の役割を details まで書き出す" {
        val arch = architecture {
            "feature".group {
                "ViewModel" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    template {
                        val feature by stringParameter()
                        file("${feature}ViewModel.kt") { "" }
                    }
                }
                "Screen" {
                    layout { "feature" / capture("feature") / "*Screen.kt".file() }
                    template { file("HomeScreen.kt") { "" } }
                }
            }
        }
        val json = withTempProject { root ->
            val output = File(root, "templateDescription.json")
            DescribeTemplates.process(
                FakeArchitectureProcessContext(
                    architecture = arch,
                    args = DescribeTemplates.Args(format = DescribeTemplatesFormat.Json, output = output.path),
                    fileSystem = ForbiddenFileSystem,
                ),
            ).getOrThrow()
            output.readText()
        }
        json shouldContain "\"roleName\": \"feature/ViewModel\""
        json.substringAfter("\"details\": [") shouldContain "\"roleName\": \"feature/Screen\""
        json.substringAfter("\"details\": [") shouldNotContain "feature/ViewModel"
    }

    "generate / undeclaredArgNames / DescribeTemplates の詳細のどの入口からでも同じ例外になる" - {
        val arch = conflicting {
            val feature by stringParameter()
            file("${feature}ViewModel.kt") { "" }
        }

        "generate" {
            shouldThrow<KatachiTemplateParameterConflictException> { arch.generated("ViewModel") }
        }
        "undeclaredArgNames" {
            shouldThrow<KatachiTemplateParameterConflictException> {
                arch.undeclaredArgNames(mapOf("roleName" to "ViewModel"))
            }
        }
        "DescribeTemplates の詳細" {
            shouldThrow<KatachiTemplateParameterConflictException> { arch.describe(roleName = "ViewModel") }
        }
    }
})
