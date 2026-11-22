package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.KatachiDuplicateFileConstraintNameException
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.architecture

/**
 * A role holds one constraint of each name: the report and the baseline tell its constraints
 * apart by that name alone.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class FileConstraintDuplicateNameSpec : FreeSpec({
    "役割に直接書いた 2 つの制約" - {
        "同じ名前なら architecture { } の評価で落ちる" {
            val thrown = shouldThrow<KatachiDuplicateFileConstraintNameException> {
                architecture {
                    "domain".group {
                        "UseCase".role {
                            fileConstraint("is public", check = silent())
                            fileConstraint("is public", check = silent())
                            layout { "useCase" / "*.kt".file() }
                        }
                    }
                }
            }

            thrown.role shouldBe "domain.UseCase"
            thrown.name shouldBe "is public"
            thrown.firstKind shouldBe "fileConstraint { }"
            thrown.kind shouldBe "fileConstraint { }"
            thrown.firstDeclaredAt.fileName shouldBe "FileConstraintDuplicateNameSpec.kt"
            thrown.declaredAt.lineNumber shouldBe thrown.firstDeclaredAt.lineNumber + 1
        }

        "文面は役割・名前・2 つの種類と位置・直し方の順に並ぶ" {
            val thrown = shouldThrow<KatachiDuplicateFileConstraintNameException> {
                architecture {
                    "UseCase".role {
                        fileConstraint("is public", check = silent())
                        fileConstraint("is public", check = silent())
                        layout { "useCase" / "*.kt".file() }
                    }
                }
            }

            thrown.message shouldBe """
                Role "UseCase" declares two constraints named "is public": fileConstraint { } at ${thrown.firstDeclaredAt} and fileConstraint { } at ${thrown.declaredAt}.
                The report and the baseline tell the constraints of a role apart by name, so a violation recorded against one would hold back a new violation of the other.
                Give each a distinct name, as in fileConstraint("<what it checks>") { ... }.
            """.trimIndent()
        }

        "名前が違えば落ちない" {
            val arch = architecture {
                "UseCase".role {
                    fileConstraint("is public", check = silent())
                    fileConstraint("has an invoke function", check = silent())
                    layout { "useCase" / "*.kt".file() }
                }
            }

            arch.declaredFileConstraints().map { it.name } shouldContainExactly
                listOf("is public", "has an invoke function")
        }

        "名前の無い fileConstraint { } は fileConstraint と名乗り、2 つ書くと断られる" {
            val failure = shouldThrow<KatachiDuplicateFileConstraintNameException> {
                architecture {
                    "UseCase".role {
                        fileConstraint(check = silent())
                        fileConstraint(check = silent())
                        layout { "useCase" / "*.kt".file() }
                    }
                }
            }

            failure.name shouldBe "fileConstraint"
        }

        "役割に直接書いた制約は、同じ場所を通る helper やループからでも別の制約として断られる" {
            fun RoleScope.noPackage(name: String) = fileConstraint(name, check = silent())

            shouldThrow<KatachiDuplicateFileConstraintNameException> {
                architecture {
                    "UseCase".role {
                        noPackage("no a")
                        noPackage("no a")
                        layout { "useCase" / "*.kt".file() }
                    }
                }
            }.name shouldBe "no a"

            shouldThrow<KatachiDuplicateFileConstraintNameException> {
                architecture {
                    "UseCase".role {
                        for (i in 1..2) fileConstraint("is public", check = silent())
                        layout { "useCase" / "*.kt".file() }
                    }
                }
            }.name shouldBe "is public"
        }

        "名前を変えた helper なら通る" {
            fun RoleScope.noPackage(name: String) = fileConstraint("no $name", check = silent())

            val arch = architecture {
                "UseCase".role {
                    noPackage("a")
                    noPackage("b")
                    layout { "useCase" / "*.kt".file() }
                }
            }

            arch.declaredFileConstraints().map { it.name } shouldContainExactly listOf("no a", "no b")
        }

        "別の役割なら同じ名前でも落ちない" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint("is public", check = silent())
                        layout { "useCase" / "*.kt".file() }
                    }
                    "Repository".role {
                        fileConstraint("is public", check = silent())
                        layout { "repository" / "*.kt".file() }
                    }
                }
            }

            arch.declaredFileConstraints().map { it.role.qualifiedName to it.name } shouldContainExactly listOf(
                "domain.UseCase" to "is public",
                "domain.Repository" to "is public",
            )
        }
    }

    "layout { } の中の制約" - {
        "役割の制約と同じ名前なら、layout が評価されたときに落ちる" {
            // layout ブロックは遅延されるので、architecture { } の評価では中の名前がまだ分からない。
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint("is public", check = silent())
                        layout {
                            "useCase" {
                                fileConstraint("is public", check = silent())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            val thrown = shouldThrow<KatachiDuplicateFileConstraintNameException> { arch.declaredFileConstraints() }
            thrown.role shouldBe "domain.UseCase"
            thrown.name shouldBe "is public"
            thrown.declaredAt.lineNumber shouldBe thrown.firstDeclaredAt.lineNumber + 3
        }

        "別のディレクトリのブロックでも、同じ役割なら落ちる" {
            val arch = architecture {
                "UseCase".role {
                    layout {
                        "core" {
                            fileConstraint("is public", check = silent())
                            "*.kt".file()
                        }
                        "feature" {
                            fileConstraint("is public", check = silent())
                            "*.kt".file()
                        }
                    }
                }
            }

            shouldThrow<KatachiDuplicateFileConstraintNameException> { arch.declaredFileConstraints() }
                .name shouldBe "is public"
        }

        "別の layout { } でも、同じ役割なら落ちる" {
            val arch = architecture {
                "UseCase".role {
                    layout { "core" { fileConstraint("is public", check = silent()) } }
                    layout { "feature" { fileConstraint("is public", check = silent()) } }
                }
            }

            shouldThrow<KatachiDuplicateFileConstraintNameException> { arch.declaredFileConstraints() }
                .name shouldBe "is public"
        }

        "ループで同じ宣言を 2 回通っても、1 つの制約なので落ちない" {
            val arch = architecture {
                "UseCase".role {
                    layout {
                        for (directory in listOf("core", "feature")) {
                            directory {
                                fileConstraint("is public", check = silent())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.declaredFileConstraints().map { it.name } shouldContainExactly listOf("is public", "is public")
        }

        "同じ制約のオブジェクトを同じ名前で 2 か所に配っても、1 つの制約なので落ちない" {
            val shared = silent()
            val arch = architecture {
                "UseCase".role {
                    layout {
                        "core" {
                            fileConstraint("is public", check = shared)
                            "*.kt".file()
                        }
                        "feature" {
                            fileConstraint("is public", check = shared)
                            "*.kt".file()
                        }
                    }
                }
            }

            arch.declaredFileConstraints().map { it.name } shouldContainExactly listOf("is public", "is public")
        }
    }
})
