package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateFileException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateFileNameException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateParameterValueException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.fs.internal.RealFileSystem
import me.tbsten.katachi.processor.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.KatachiExistingTemplateFileException
import me.tbsten.katachi.template.OnExisting

/** A role that produces an interface and its implementation, which is the shape v0.2 is built for. */
private fun useCaseArchitecture(): Architecture = architecture {
    "domain".group {
        "UseCase" {
            layout { "useCase" / "*UseCase.kt".file() }
            layout { "useCase" / "impl" / "*UseCaseImpl.kt".file() }
            template {
                val name by stringParameter()
                val implBody by stringParameter(default = """TODO("not implemented")""")

                file("${name}UseCase.kt") {
                    """
                    interface ${name}UseCase {
                        suspend operator fun invoke()
                    }
                    """.trimIndent()
                }
                file("${name}UseCaseImpl.kt") {
                    """
                    class ${name}UseCaseImpl : ${name}UseCase {
                        override suspend fun invoke() {
                            $implBody
                        }
                    }
                    """.trimIndent()
                }
            }
        }
    }
}

/** A role whose implementation file is produced only when `withImpl` says so. */
private fun repositoryArchitecture(): Architecture = architecture {
    "data".group {
        "Repository" {
            layout {
                "repository" / "*Repository.kt".file()
                "repository" / "*RepositoryImpl.kt".file()
            }
            template {
                val name by stringParameter()
                val withImpl by booleanParameter(default = true)

                file("${name}Repository.kt") { "interface ${name}Repository" }
                if (withImpl) {
                    file("${name}RepositoryImpl.kt") { "class ${name}RepositoryImpl : ${name}Repository" }
                }
            }
        }
    }
}

/**
 * Runs the processor against [root] as the project, with [rawArgs] as the run's `--arg` map.
 *
 * The context is built by hand rather than through `Architecture.process`, because the values of a
 * template's own parameters travel as raw arguments and that overload has no command line to take
 * them from.
 */
private fun Architecture.generateInto(
    root: File,
    rawArgs: Map<String, String>,
    onExisting: OnExisting = OnExisting.Fail,
): List<String> {
    val context = FakeArchitectureProcessContext(
        architecture = this,
        args = GenerateCodeFromTemplate.Args(
            roleName = rawArgs.getValue("roleName"),
            onExisting = onExisting,
        ),
        fileSystem = RealFileSystem(root),
        rawArgs = rawArgs,
    )
    // Every run that gets this far wrote its files, so it has to answer success; the ways it
    // can refuse all come back as a failure, which `getOrThrow` turns into the throw the specs of
    // those refusals catch.
    GenerateCodeFromTemplate.process(context).getOrThrow()
    return context.logs
}

/**
 * The whole path: a role, a template, `--arg` values, and files on a disk.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.template` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class GenerateCodeFromTemplateSpec : FreeSpec({
    "--arg の値がテンプレートに届く" - {
        "宣言していない --arg がパラメータとして読める" {
            withTempProject { root ->
                useCaseArchitecture().generateInto(
                    root,
                    mapOf("roleName" to "UseCase", "name" to "GetUser"),
                )

                root.relativeFilePaths() shouldContainExactly listOf(
                    "gradlew",
                    "useCase/GetUserUseCase.kt",
                    "useCase/impl/GetUserUseCaseImpl.kt",
                )
                File(root, "useCase/GetUserUseCase.kt").readText() shouldContain
                    "interface GetUserUseCase"
            }
        }

        "値を渡さなかったパラメータは宣言した既定値になる" {
            withTempProject { root ->
                useCaseArchitecture().generateInto(
                    root,
                    mapOf("roleName" to "UseCase", "name" to "GetUser"),
                )

                File(root, "useCase/impl/GetUserUseCaseImpl.kt").readText() shouldContain
                    """TODO("not implemented")"""
            }
        }

        "渡した値が既定値に優先する" {
            withTempProject { root ->
                useCaseArchitecture().generateInto(
                    root,
                    mapOf("roleName" to "UseCase", "name" to "GetUser", "implBody" to "error(1)"),
                )

                File(root, "useCase/impl/GetUserUseCaseImpl.kt").readText() shouldContain "error(1)"
            }
        }

        "修飾名でも役割に届く" {
            withTempProject { root ->
                useCaseArchitecture().generateInto(
                    root,
                    mapOf("roleName" to "domain/UseCase", "name" to "GetUser"),
                )

                File(root, "useCase/GetUserUseCase.kt").exists() shouldBe true
            }
        }
    }

    "書き出しは all-or-nothing" - {
        "2回目は既定で落ち、既存のファイルを書き換えない" {
            withTempProject { root ->
                val values = mapOf("roleName" to "UseCase", "name" to "GetUser")
                useCaseArchitecture().generateInto(root, values)
                File(root, "useCase/GetUserUseCase.kt").writeText("edited by hand")
                File(root, "useCase/impl/GetUserUseCaseImpl.kt").delete()

                val thrown = shouldThrow<KatachiExistingTemplateFileException> {
                    useCaseArchitecture().generateInto(root, values)
                }

                thrown.existing shouldContainExactly listOf("useCase/GetUserUseCase.kt")
                File(root, "useCase/GetUserUseCase.kt").readText() shouldBe "edited by hand"
                // Deleted above and not put back: the run wrote nothing at all.
                File(root, "useCase/impl/GetUserUseCaseImpl.kt").exists() shouldBe false
            }
        }

        "skip なら2回目は何も書かずに成功する" {
            withTempProject { root ->
                val values = mapOf("roleName" to "UseCase", "name" to "GetUser")
                useCaseArchitecture().generateInto(root, values)
                File(root, "useCase/GetUserUseCase.kt").writeText("edited by hand")

                val logs = useCaseArchitecture().generateInto(root, values, OnExisting.Skip)

                File(root, "useCase/GetUserUseCase.kt").readText() shouldBe "edited by hand"
                logs.last() shouldContain "Wrote nothing"
            }
        }
    }

    "型付きのパラメータ" - {
        "withImpl=false では Impl のファイルが書かれない" {
            withTempProject { root ->
                repositoryArchitecture().generateInto(
                    root,
                    mapOf("roleName" to "Repository", "name" to "User", "withImpl" to "false"),
                )

                root.relativeFilePaths() shouldContainExactly listOf(
                    "gradlew",
                    "repository/UserRepository.kt",
                )
            }
        }

        "読めない値で落ちたときは 1 つもファイルが書かれない" {
            withTempProject { root ->
                shouldThrow<KatachiInvalidTemplateParameterValueException> {
                    repositoryArchitecture().generateInto(
                        root,
                        mapOf("roleName" to "Repository", "name" to "User", "withImpl" to "yes"),
                    )
                }

                root.relativeFilePaths() shouldContainExactly listOf("gradlew")
            }
        }
    }

    "定義の間違いはディスクに触る前に落ちる" - {
        "値の足りないパラメータは、ファイルを1つも作らずに落ちる" {
            withTempProject { root ->
                shouldThrow<me.tbsten.katachi.dsl.KatachiMissingTemplateParameterException> {
                    useCaseArchitecture().generateInto(root, mapOf("roleName" to "UseCase"))
                }

                root.relativeFilePaths() shouldContainExactly listOf("gradlew")
            }
        }
    }

    "定義の間違いは、プロジェクトルートを探す前に出る" - {
        // KatachiInvalidTemplateFileNameException / KatachiDuplicateTemplateFileException の
        // KDoc の利用例そのもの。template { } は block を持っておくだけなので、この2つは
        // architecture { } では投げない -- 利用例がそう書かれていて、実行すると落ちた。
        "パスを含む file() は、template を走らせたときに failure になる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { template { file("useCase/GetUserUseCase.kt") { "" } } }
                }
            }

            withClue("architecture { } の時点では何も起きない") {
                shouldNotThrowAny { arch }
            }
            val result = shouldNotThrowAny {
                arch.process(
                    GenerateCodeFromTemplate,
                    GenerateCodeFromTemplate.Args(roleName = "UseCase"),
                )
            }
            result.exceptionOrNull().shouldBeInstanceOf<KatachiInvalidTemplateFileNameException>()
                .fileName shouldBe "useCase/GetUserUseCase.kt"
        }

        "同じ名前を2回宣言した template も、走らせたときに failure になる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        template {
                            file("GetUserUseCase.kt") { "" }
                            file("GetUserUseCase.kt") { "" }
                        }
                    }
                }
            }

            val result = shouldNotThrowAny {
                arch.process(
                    GenerateCodeFromTemplate,
                    GenerateCodeFromTemplate.Args(roleName = "UseCase"),
                )
            }
            result.exceptionOrNull().shouldBeInstanceOf<KatachiDuplicateTemplateFileException>()
                .fileName shouldBe "GetUserUseCase.kt"
        }
    }
})
