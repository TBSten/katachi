package me.tbsten.katachi.test.docs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.core.spec.style.FreeSpec
import me.tbsten.katachi.docs.KatachiDocumentPathCollisionException
import me.tbsten.katachi.dsl.architecture

/**
 * Which pages a definition produces, and where they land.
 *
 * What is *on* a page is [ContainerPageSpec]'s and [RolePageSpec]'s.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class RoleReferenceSpec : FreeSpec({
    "出力の構造" - {
        "group がディレクトリ、役割が識別子のファイルになる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { }
                    "Repository" { }
                }
            }

            arch.documents().keys.toList() shouldContainExactly listOf(
                "README.md",
                "domain/README.md",
                "domain/UseCase.md",
                "domain/Repository.md",
            )
        }

        "ネストした group はそのままディレクトリのネストになる" {
            val arch = architecture {
                "domain".group {
                    "model".group {
                        "Entity" { }
                    }
                }
            }

            arch.documents().keys.toList() shouldContainExactly listOf(
                "README.md",
                "domain/README.md",
                "domain/model/README.md",
                "domain/model/Entity.md",
            )
        }

        "group に属さない役割は出力ルートの直下に出る" {
            val arch = architecture {
                "Readme" { }
                "domain".group { "UseCase" { } }
            }

            arch.documents().keys.toList() shouldContainExactly listOf(
                "README.md",
                "Readme.md",
                "domain/README.md",
                "domain/UseCase.md",
            )
        }

        "ファイル名は識別子で、title を変えても変わらない" {
            val arch = architecture {
                "domain".group {
                    title = "ドメイン"
                    "UseCase" { title = "ユースケース" }
                }
            }

            arch.documents().keys.toList() shouldContainExactly listOf(
                "README.md",
                "domain/README.md",
                "domain/UseCase.md",
            )
        }
    }

    "documented = false" - {
        "指定した役割のページが生成されない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { }
                    "Internal" { documented = false }
                }
            }

            arch.documents().keys.toList() shouldContainExactly listOf(
                "README.md",
                "domain/README.md",
                "domain/UseCase.md",
            )
        }

        "指定した group は README も中の役割のページも持たない" {
            val arch = architecture {
                "build".group {
                    documented = false
                    "GradleModule" { }
                }
                "domain".group { "UseCase" { } }
            }

            arch.documents().keys.toList() shouldContainExactly listOf(
                "README.md",
                "domain/README.md",
                "domain/UseCase.md",
            )
        }

        "指定した group の中にネストした group も出ない" {
            val arch = architecture {
                "build".group {
                    documented = false
                    "logic".group { "Convention" { } }
                }
            }

            arch.documents().keys.toList() shouldContainExactly listOf("README.md")
        }

        "ルート README の group 一覧からも外れる" {
            val arch = architecture {
                "build".group { documented = false }
                "domain".group { }
            }

            withClue("ページが無い group へのリンクは、出た瞬間にリンク切れになる") {
                arch.page("README.md") shouldBe
                    """
                    # アーキテクチャ

                    ## グループ

                    - [domain](./domain/README.md)
                    """.trimIndent() + "\n"
            }
        }
    }

    "識別子の衝突" - {
        "group の README と同じ名前の役割は例外になる" {
            val arch = architecture {
                "domain".group {
                    "README" { }
                }
            }

            val exception = shouldThrow<KatachiDocumentPathCollisionException> { arch.documents() }

            exception.path shouldBe "domain/README.md"
            exception.message.orEmpty() shouldContain "Group \"domain\" already produced it"
        }

        "ルート README と同じ名前の役割も例外になる" {
            val arch = architecture { "README" { } }

            val exception = shouldThrow<KatachiDocumentPathCollisionException> { arch.documents() }

            exception.path shouldBe "README.md"
            withClue("ルートは定義のどの行からも生まれないので、指す先が無い") {
                exception.message.orEmpty() shouldContain "The documentation root already produced it, and"
            }
        }

        "衝突の例外は宣言位置を両方とも名指しする" {
            val arch = architecture {
                "domain".group {
                    "README" { }
                }
            }

            val exception = shouldThrow<KatachiDocumentPathCollisionException> { arch.documents() }

            exception.firstDeclaredAt.fileName shouldBe "RoleReferenceSpec.kt"
            exception.declaredAt.fileName shouldBe "RoleReferenceSpec.kt"
        }
    }

    "ページの組み立てはファイルシステムを1度も読まない" {
        val arch = architecture {
            "domain".group {
                "UseCase" { layout { "useCase" { "*UseCase.kt".file() } } }
            }
        }

        withClue("読んだら ForbiddenFileSystem が投げるので、例外が出ないことが証拠になる") {
            arch.documents().keys.size shouldBe 3
        }
    }

    "ファイル構成の節は declarations { } がまだ無いので出ない" {
        val arch = architecture {
            "domain".group {
                "UseCase" { layout { "useCase" { "*UseCase.kt".file() } } }
            }
        }

        arch.documents().values.forEach { page ->
            page shouldNotContainSection "## ファイル構成"
        }
    }
})

/** Reads better than a negated `shouldContain` in a loop, and says what the loop is looking for. */
private infix fun String.shouldNotContainSection(heading: String) {
    withClue("$heading が出ている:\n$this") {
        contains(heading) shouldBe false
    }
}
