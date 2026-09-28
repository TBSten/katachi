package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.ModuleResolver
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.wildcard
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
import me.tbsten.katachi.template.KatachiTemplateModuleNotFoundException
import me.tbsten.katachi.template.KatachiWildcardTemplatePlacementException
import me.tbsten.katachi.template.internal.templateFiles
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.files.FakeFileSystemScope
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem
import me.tbsten.katachi.test.dsl.moduleIndexOf

/**
 * Runs [roleName]'s template with the modules [modules] answers, against a tree that refuses to be
 * read otherwise.
 */
private fun Architecture.generatedWith(
    roleName: String,
    values: Map<String, String>,
    modules: () -> ModuleIndex,
): Map<String, String> = process(ForbiddenFileSystem) { context ->
    templateFiles(context, roleName, values, modules)
}

private val features: () -> ModuleIndex = { moduleIndexOf("feature/home", "feature/settings") }

private val modulePackage = capitalizedModuleNamePackage("com.example")

/** A Screen role in every feature module, its file named after the module. */
private fun screenArchitecture(): Architecture = architecture {
    files = wholeTree()
    "feature".group {
        "Screen" {
            layout {
                ":feature:*".module(capture = "feature") {
                    mainSourceSet / kotlin / modulePackage / "${wildcard("feature").pascalCase}*Screen".ktFile()
                }
            }
            template {
                val name by stringParameter()
                val feature = captureValue("feature")
                file("${feature.pascalCase}${name}Screen.kt") { "package com.example.feature.$feature" }
            }
        }
    }
}

/**
 * Where a generated file lands when the module is a wildcard named with
 * `.module(capture = "...")`: the module has to exist, and it is looked up only then.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.template` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class TemplateModuleCapturePlacementSpec : FreeSpec({
    "モジュールの capture" - {
        "実在するモジュールを渡すとそのモジュールに、modulePackage と wildcard 由来のファイル名で生成される" {
            val generated = screenArchitecture().generatedWith(
                "Screen",
                mapOf("feature" to "home", "name" to "List"),
                features,
            )

            val path = generated.keys.single()
            path shouldStartWith "feature/home/src/main/kotlin/"
            path shouldEndWith "/HomeListScreen.kt"
            path shouldNotContain "*"
            path shouldNotContain "<name>"
            generated.values.single() shouldBe "package com.example.feature.home\n"
        }

        "実在しないモジュールを渡すと KatachiTemplateModuleNotFoundException で実在するモジュールを並べる" {
            val thrown = shouldThrow<KatachiTemplateModuleNotFoundException> {
                screenArchitecture().generatedWith("Screen", mapOf("feature" to "hoem", "name" to "List"), features)
            }

            thrown.modulePattern shouldBe ":feature:*"
            thrown.modulePath shouldBe ":feature:hoem"
            thrown.existing shouldContainExactly listOf(":feature:home", ":feature:settings")
            thrown.message.orEmpty() shouldContain "[UnexpectedDirectory]"
        }

        "実在しないモジュールの案内は、モジュールパスではなく --arg に渡す値の形で出す" {
            val thrown = shouldThrow<KatachiTemplateModuleNotFoundException> {
                screenArchitecture().generatedWith("Screen", mapOf("feature" to "hoem", "name" to "List"), features)
            }

            thrown.captureNames shouldBe listOf("feature")
            thrown.existingArgs shouldContainExactly listOf("--arg feature=home", "--arg feature=settings")
            thrown.message.orEmpty() shouldContain "--arg feature=home"
            thrown.message.orEmpty() shouldContain "--arg feature=settings"
            thrown.message.orEmpty() shouldContain "not `:feature:home`"
        }

        "* が2つのキーの案内は、モジュールごとに2つの --arg を並べる" {
            val arch = architecture {
                "Api" {
                    layout { ":core:*:*".module("layer", "part") { "*Api.kt".file() } }
                    template { file("UserApi.kt") { "" } }
                }
            }
            val modules = { moduleIndexOf("core/data/remote", "core/domain/local") }

            val thrown = shouldThrow<KatachiTemplateModuleNotFoundException> {
                arch.generatedWith("Api", mapOf("layer" to "data", "part" to "local"), modules)
            }
            thrown.existingArgs shouldContainExactly listOf(
                "--arg layer=data --arg part=remote",
                "--arg layer=domain --arg part=local",
            )
        }

        "打ち間違えた値は、別の具体的なパスが受け取れても KatachiTemplateModuleNotFoundException" {
            val arch = architecture {
                "Screen" {
                    layout {
                        ":feature:*".module(capture = "feature") { "*Screen.kt".file() }
                        ":app".module { "*Screen.kt".file() }
                    }
                    template { file("HomeScreen.kt") { "" } }
                }
            }
            val modules = { moduleIndexOf("app", "feature/home") }

            shouldThrow<KatachiTemplateModuleNotFoundException> {
                arch.generatedWith("Screen", mapOf("feature" to "hoem"), modules)
            }.modulePath shouldBe ":feature:hoem"
        }

        "モジュールの capture に1セグメントでない値を渡すと、モジュールを探さずに KatachiInvalidTemplateCaptureValueException" {
            var scans = 0
            listOf("home/list", "..", "").forEach { value ->
                shouldThrow<KatachiInvalidTemplateCaptureValueException> {
                    screenArchitecture().generatedWith("Screen", mapOf("feature" to value, "name" to "List")) {
                        scans++
                        features()
                    }
                }.name shouldBe "feature"
            }
            scans shouldBe 0
        }

        "部分一致のモジュールキーにも名前を付けられ、値は * の部分だけを受け取る" {
            val arch = architecture {
                "Screen" {
                    layout { ":feature:*-impl".module(capture = "feature") { "*Screen.kt".file() } }
                    template { file("HomeScreen.kt") { "" } }
                }
            }
            val modules = { moduleIndexOf("feature/home-impl", "feature/settings-impl") }

            arch.generatedWith("Screen", mapOf("feature" to "home"), modules).keys shouldBe
                setOf("feature/home-impl/HomeScreen.kt")
        }

        "1つのパスでモジュールの capture とパスの capture を併用できる" - {
            "規約どおりのディレクトリのモジュール" {
                val arch = architecture {
                    "Screen" {
                        layout {
                            ":feature:*".module(capture = "feature") { "src" / capture("layer") / "*Screen.kt".file() }
                        }
                        template { file("HomeScreen.kt") { "" } }
                    }
                }

                arch.generatedWith("Screen", mapOf("feature" to "home", "layer" to "ui"), features).keys shouldBe
                    setOf("feature/home/src/ui/HomeScreen.kt")
            }

            "moduleResolver で階層の数が規約と違うモジュール" {
                // The resolver puts `:feature:home`'s sources one level below its directory, the
                // way a `projectDir` override in settings.gradle.kts can, so the path capture sits
                // at another segment index than under the conventional directory. Discovery finds
                // the modules by their build files and does not consult the resolver.
                val resolver = ModuleResolver { module -> module.segments.joinToString("/") + "/android" }
                val arch = architecture {
                    moduleResolver = resolver
                    "Screen" {
                        layout {
                            ":feature:*".module(capture = "feature") { "src" / capture("layer") / "*Screen.kt".file() }
                        }
                        template { file("HomeScreen.kt") { "" } }
                    }
                }
                val modules = { moduleIndexOf("feature/home", "feature/settings", resolver = resolver) }

                arch.generatedWith("Screen", mapOf("feature" to "home", "layer" to "ui"), modules).keys shouldBe
                    setOf("feature/home/android/src/ui/HomeScreen.kt")
            }
        }

        "* が2つのキーは2つの値で1つのモジュールに決まる" {
            val arch = architecture {
                "Api" {
                    layout { ":core:*:*".module("layer", "part") { "*Api.kt".file() } }
                    template { file("UserApi.kt") { "" } }
                }
            }
            val modules = { moduleIndexOf("core/data/remote", "core/data/local", "core/domain/remote") }

            arch.generatedWith("Api", mapOf("layer" to "data", "part" to "remote"), modules).keys shouldBe
                setOf("core/data/remote/UserApi.kt")
        }

        "capture の値が無いと、モジュールを探さずに KatachiMissingTemplateCaptureException" {
            var scans = 0
            val arch = architecture {
                "Screen" {
                    layout {
                        ":feature:*".module(capture = "feature") { "${wildcard("feature").pascalCase}*Screen".ktFile() }
                    }
                    template { file("HomeListScreen.kt") { "" } }
                }
            }
            val thrown = shouldThrow<KatachiMissingTemplateCaptureException> {
                arch.generatedWith("Screen", emptyMap()) {
                    scans++
                    features()
                }
            }

            thrown.missing.values.flatten() shouldBe listOf("feature")
            scans shouldBe 0
        }

        "名前の無い module キーは今までどおり KatachiWildcardTemplatePlacementException" {
            val arch = architecture {
                "Screen" {
                    layout { ":feature:*".module { "*Screen.kt".file() } }
                    template { file("HomeScreen.kt") { "" } }
                }
            }

            shouldThrow<KatachiWildcardTemplatePlacementException> {
                arch.generatedWith("Screen", mapOf("feature" to "home"), features)
            }
        }

        "モジュールの capture を持たない役割はモジュールを探さない" {
            var scans = 0
            val arch = architecture {
                "ViewModel" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    template { file("HomeViewModel.kt") { "" } }
                }
            }

            arch.generatedWith("ViewModel", mapOf("feature" to "home")) {
                scans++
                features()
            }.keys shouldBe setOf("feature/home/HomeViewModel.kt")
            scans shouldBe 0
        }
    }

    "生成物がそのまま検査を通る" - {
        "パスの capture で生成したファイルは Error を1つも出さない" {
            val arch = architecture {
                files = wholeTree()
                "ViewModel" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    template { file("HomeViewModel.kt") { "" } }
                }
            }
            val path = arch.generated("ViewModel", mapOf("feature" to "home")).keys.single()

            arch.validate(repositoryOf { file(path) }).filter { it.severity == Severity.Error }.shouldBeEmpty()
        }

        "モジュールの capture で生成したファイルは Error を1つも出さない" {
            val arch = screenArchitecture()
            val path = arch.generatedWith("Screen", mapOf("feature" to "home", "name" to "List"), features).keys.single()
            val tree = repositoryOf {
                "feature/home/build.gradle.kts"()
                "feature/settings/build.gradle.kts"()
                file(path)
            }

            arch.validate(tree).filter { it.severity == Severity.Error }.shouldBeEmpty()
        }
    }
})

/** A file at [path], relative to the enclosing block. */
private fun FakeFileSystemScope.file(path: String) {
    path()
}
