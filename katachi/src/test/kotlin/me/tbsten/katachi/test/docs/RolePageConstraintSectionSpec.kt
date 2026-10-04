package me.tbsten.katachi.test.docs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.docs.internal.roleReferenceDocuments
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintDocumentContext
import me.tbsten.katachi.dsl.FileConstraintExampleFile
import me.tbsten.katachi.dsl.FileConstraintExampleSource
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.test.dsl.files.fakeFileSystem

/**
 * The section a constraint writes about itself on its role's page
 * ([FileConstraint.documentSection]), and the example file it is told about.
 *
 * Most cases build the pages through [documents], over a tree that refuses to be read: a page
 * whose constraints never ask for an example file must not list the project.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */

/** A constraint that rejects nothing and writes [section] about itself. */
private class DescribedConstraint(
    private val section: (FileConstraintDocumentContext) -> String?,
) : FileConstraint {
    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> = emptyList()

    override fun documentSection(context: FileConstraintDocumentContext): String? = section(context)
}

/** A constraint that keeps the example file it was told about, and writes a section naming it. */
private class ExampleRecordingConstraint : FileConstraint {
    var example: FileConstraintExampleFile? = null

    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> = emptyList()

    override fun documentSection(context: FileConstraintDocumentContext): String {
        val file = context.exampleFile
        example = file
        return "## Example file\n\n`${file.fileName}`"
    }
}

class RolePageConstraintSectionSpec : FreeSpec({
    "節の置き場所" - {
        "節は ## Constraints の後、## Examples の前に出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        example("GetUserUseCase")
                        fileConstraint("has a section", check = DescribedConstraint { "## Custom\n\nBody." })
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldContain
                """
                ## Constraints

                - has a section

                ## Custom

                Body.

                ## Examples

                - `GetUserUseCase`
                """.trimIndent()
        }

        "複数の制約の節は書いた順に並ぶ" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint(check = DescribedConstraint { "## First" })
                        layout {
                            "useCase" {
                                fileConstraint(check = DescribedConstraint { "## Second" })
                                "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            arch.page("domain/UseCase.md").trimEnd().lines().takeLast(3) shouldBe listOf("## First", "", "## Second")
        }

        "前後の空行は落とし、空の節は出さない" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint(check = DescribedConstraint { "\n\n## Trimmed\n\nBody.\n\n\n" })
                        fileConstraint(check = DescribedConstraint { "  \n  " })
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldContain "|\n\n## Trimmed\n\nBody.\n"
            arch.page("domain/UseCase.md").endsWith("## Trimmed\n\nBody.\n") shouldBe true
        }

        "節を書かない制約しかない役割のページは今と同じ" {
            val withoutSection = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint("has a name") { emptyList() }
                        fileConstraint(check = DescribedConstraint { null })
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                }
            }
            val plain = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint("has a name") { emptyList() }
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                }
            }

            withoutSection.page("domain/UseCase.md") shouldBe plain.page("domain/UseCase.md")
        }

        "同じ制約を 2 か所に配っても節は 1 つ" {
            val shared = DescribedConstraint { "## Shared" }
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        layout {
                            "alpha" {
                                fileConstraint(check = shared)
                                "*.kt".file()
                            }
                            "beta" {
                                fileConstraint(check = shared)
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.page("domain/UseCase.md").split("## Shared").size shouldBe 2
        }

        "同じ場所・同じ名前で書いた制約は、別のインスタンスでも節は 1 つ" {
            // A block that builds its check, run once per place it is handed to: what a
            // constraint inside a wildcard module key looks like, one instance per module.
            val site = DeclarationSite("UseCaseRole.kt", 12)
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        layout {
                            for (directory in listOf("alpha", "beta")) {
                                directory {
                                    fileConstraint("built", site, check = DescribedConstraint { "## Built" })
                                    "*.kt".file()
                                }
                            }
                        }
                    }
                }
            }

            arch.page("domain/UseCase.md").split("## Built").size shouldBe 2
        }

        "名前が違えば、同じ場所で書いた制約の節はそれぞれ出る" {
            val site = DeclarationSite("UseCaseRole.kt", 12)
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint("first", site, check = DescribedConstraint { "## First" })
                        fileConstraint("second", site, check = DescribedConstraint { "## Second" })
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                }
            }

            val page = arch.page("domain/UseCase.md")
            page shouldContain "## First"
            page shouldContain "## Second"
        }

        "節を書くときに投げた例外は、そのまま生成を止める" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint(check = DescribedConstraint { throw IllegalStateException("cannot describe") })
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                }
            }

            shouldThrow<IllegalStateException> { arch.documents() }.message shouldBe "cannot describe"
        }

        "ドキュメントを出さない役割の制約には節を聞かない" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        documented = false
                        fileConstraint(check = DescribedConstraint { throw IllegalStateException("asked") })
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                }
            }

            ("domain/UseCase.md" in arch.documents().keys) shouldBe false
        }
    }

    "例のファイル" - {
        "役割の example があれば、その名前の .kt と、ファイル名から分かる capture" {
            val recording = ExampleRecordingConstraint()
            val arch = architecture {
                "feature".group {
                    "ViewModel".role {
                        example("HomeViewModel", "the home screen")
                        fileConstraint(check = recording)
                        layout { "feature" / capture("feature") / "${capture("name")}ViewModel".ktFile() }
                    }
                }
            }

            arch.page("feature/ViewModel.md") shouldContain "## Example file\n\n`HomeViewModel.kt`"
            val example = recording.example.shouldNotBeNull()
            example.fileName shouldBe "HomeViewModel.kt"
            example.path.shouldBeNull()
            example.captureValues shouldBe mapOf("name" to "Home")
            example.source shouldBe FileConstraintExampleSource.RoleExample
        }

        "example に . があれば、そのままファイル名として読む" {
            val recording = ExampleRecordingConstraint()
            val arch = architecture {
                "domain".group {
                    "Platform".role {
                        example("Platform.android.kt")
                        fileConstraint(check = recording)
                        layout { "platform" / "*.kt".file() }
                    }
                }
            }

            arch.documents()
            recording.example.shouldNotBeNull().fileName shouldBe "Platform.android.kt"
        }

        "example が無く、プロジェクトに覆うファイルがあれば、最初のファイルとそのパスの capture" {
            val recording = ExampleRecordingConstraint()
            val arch = architecture {
                files = wholeTree()
                "feature".group {
                    "ViewModel".role {
                        fileConstraint(check = recording)
                        layout { "feature" / capture("feature") / "${capture("name")}ViewModel".ktFile() }
                    }
                }
            }
            val tree = fakeFileSystem("/repo") {
                "/repo" {
                    ".git" { "HEAD"() }
                    "feature/home" { "HomeViewModel.kt"() }
                }
            }

            arch.process(tree) { context -> roleReferenceDocuments(context) }

            val example = recording.example.shouldNotBeNull()
            example.fileName shouldBe "HomeViewModel.kt"
            example.path shouldBe "feature/home/HomeViewModel.kt"
            example.captureValues shouldBe mapOf("feature" to "home", "name" to "Home")
            example.source shouldBe FileConstraintExampleSource.MatchedFile
        }

        "example もプロジェクトも無ければ Sample.kt" {
            val recording = ExampleRecordingConstraint()
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint(check = recording)
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                }
            }

            // `documents()` builds the pages over a tree that throws on every read.
            arch.documents()

            val example = recording.example.shouldNotBeNull()
            example.fileName shouldBe "Sample.kt"
            example.path.shouldBeNull()
            example.captureValues shouldBe emptyMap()
            example.source shouldBe FileConstraintExampleSource.Placeholder
        }

        "節を書くときに例のファイルを読まなければ、プロジェクトは読まない" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint(check = DescribedConstraint { context -> "## Names\n\n${context.captureNames}" })
                        layout { "useCase" / capture("name") / "*UseCase".ktFile() }
                    }
                }
            }

            // Over a tree that throws on every read: reaching the project would fail the spec.
            arch.page("domain/UseCase.md") shouldContain "## Names\n\n[name]"
        }
    }
})
