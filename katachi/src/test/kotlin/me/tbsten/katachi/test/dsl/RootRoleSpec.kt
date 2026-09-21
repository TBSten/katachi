package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.DeclarationKind
import me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.flattenLayout
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.test.fs.ForbiddenFileSystem

/**
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない。
 * 宣言位置の捕捉が katachi 自身のフレームとして読み飛ばしてしまう（DeclarationSiteSpec 参照）。
 */
class RootRoleSpec : FreeSpec({
    "宣言" - {
        "architecture { } の直下に役割を宣言でき、allRoles に入る" {
            val arch = architecture {
                "UseCase" { }
            }

            arch.allRoles.map { it.name } shouldContainExactly listOf("UseCase")
        }

        "ルート直下の役割の qualifiedName は役割名そのもので、groupPath は空になる" {
            val arch = architecture {
                "UseCase" { }
            }

            val role = arch.allRoles.single()
            role.qualifiedName shouldBe "UseCase"
            role.groupPath.shouldBeEmpty()
        }

        "ルート直下の役割と group 直下の役割を混ぜて宣言できる" {
            val arch = architecture {
                "Readme" { }
                "domain".group {
                    "UseCase" { }
                    "model".group { "Entity" { } }
                }
                "Gitignore" { }
            }

            arch.roles.map { it.name } shouldContainExactly listOf("Readme", "Gitignore")
            arch.groups.map { it.name } shouldContainExactly listOf("domain")
            arch.allRoles.map { it.qualifiedName } shouldContainExactly
                listOf("Readme", "Gitignore", "domain/UseCase", "domain/model/Entity")
        }

        "group を1つも持たない定義でも役割を宣言できる" {
            val arch = architecture {
                "Readme" { }
            }

            arch.groups.shouldBeEmpty()
            arch.allRoles.single().qualifiedName shouldBe "Readme"
        }

        "ルート直下の役割も group 内と同じプロパティを書ける" {
            val arch = architecture {
                "Readme" { title = "README" }
            }

            arch.allRoles.single()[Title] shouldBe "README"
        }

        "ルート直下の役割の宣言位置はそれを書いた行を指す" {
            val arch = architecture {
                "Readme" { }
            }

            // この行番号はファイル内の位置に依存する。上のブロックを動かしたら直すこと。
            arch.allRoles.single().declaredAt.fileName shouldBe "RootRoleSpec.kt"
            arch.allRoles.single().declaredAt.lineNumber shouldBe 75
        }
    }

    "layout" - {
        "ルート直下の役割の layout { } が平坦化される" {
            val arch = architecture {
                "Gitignore" { layout { ".gitignore".file() } }
            }

            arch.flattenLayout().map { it.path } shouldContainExactly listOf(".gitignore")
        }

        "ルート直下の役割の layout は group 内の役割の layout と並んで平坦化される" {
            val arch = architecture {
                "Gitignore" { layout { ".gitignore".file() } }
                "domain".group {
                    "UseCase" { layout { "useCase" { } } }
                }
            }

            arch.flattenLayout().map { it.role.qualifiedName to it.path } shouldContainExactly
                listOf("Gitignore" to ".gitignore", "domain/UseCase" to "useCase")
        }

        "processor のモデルからもルート直下の役割とそのエントリが見える" {
            val arch = architecture {
                "Gitignore" { layout { ".gitignore".file() } }
            }

            // 宣言しか読まないので、ファイルシステムに触ったら落ちるものを渡してよい。
            arch.process(ForbiddenFileSystem) { model ->
                model.roles.map { it.qualifiedName } + model.declaredEntries.map { it.path }
            } shouldContainExactly listOf("Gitignore", ".gitignore")
        }
    }

    "名前の衝突" - {
        "ルート直下で同名の役割を2回宣言するとエラーになる" {
            val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
                architecture {
                    "Readme" { }
                    "Readme" { }
                }
            }

            thrown.name shouldBe "Readme"
            thrown.kind shouldBe DeclarationKind.Role
            thrown.firstKind shouldBe DeclarationKind.Role
            thrown.scope shouldBe "the root of architecture { }"
        }

        "ルート直下で group と同名の役割を宣言するとエラーになる" {
            val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
                architecture {
                    "domain".group { }
                    "domain" { }
                }
            }

            thrown.name shouldBe "domain"
            thrown.kind shouldBe DeclarationKind.Role
            thrown.firstKind shouldBe DeclarationKind.Group
        }

        "ルート直下で役割と同名の group を宣言してもエラーになる" {
            val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
                architecture {
                    "domain" { }
                    "domain".group { }
                }
            }

            thrown.name shouldBe "domain"
            thrown.kind shouldBe DeclarationKind.Group
            thrown.firstKind shouldBe DeclarationKind.Role
        }

        "group と役割の衝突は、同種の重複とは違う理由と直し方を出す" {
            val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
                architecture {
                    "domain".group { }
                    "domain" { }
                }
            }

            thrown.message shouldBe listOf(
                "Duplicate role \"domain\" declared at ${thrown.declaredAt}.",
                "A group of that name was already declared at ${thrown.firstDeclaredAt}, " +
                    "in the root of architecture { }.",
                "A group and a role in the root of architecture { } would both be referred to " +
                    "as \"domain\". Rename one of them, or move the role into a group.",
            ).joinToString("\n")
        }

        "外側のスコープを掴んで、評価中のルート直下の役割と同名の group を差し込んでもエラーになる" {
            val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
                architecture {
                    val root = this
                    "domain" {
                        with(root) { "domain".group { } }
                    }
                }
            }

            thrown.name shouldBe "domain"
        }

        // ルートだけが group と役割で名前空間を共有する。group の中は従来どおり別々で、
        // ここが落ちたら ArchitectureScopeImpl / GroupScopeImpl の非対称が崩れている。
        "group の中では同名の group と役割を宣言できる" {
            val arch = architecture {
                "x".group {
                    "domain".group { }
                    "domain" { }
                }
            }

            arch.allGroups.map { it.qualifiedName } shouldContainExactly listOf("x", "x/domain")
            arch.allRoles.map { it.qualifiedName } shouldContainExactly listOf("x/domain")
        }

        "ルート直下の役割と、group の中の同名の役割は衝突しない" {
            val arch = architecture {
                "UseCase" { }
                "domain".group { "UseCase" { } }
            }

            arch.allRoles.map { it.qualifiedName } shouldContainExactly
                listOf("UseCase", "domain/UseCase")
        }
    }
})
