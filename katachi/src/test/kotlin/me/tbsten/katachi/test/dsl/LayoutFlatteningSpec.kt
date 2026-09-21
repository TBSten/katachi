package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.GlobSyntaxException
import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.flattenLayout
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile

@OptIn(InternalKatachiApi::class)
class LayoutFlatteningSpec : FreeSpec({
    "キーの種類" - {
        "layout 直下のファイルはリポジトリルート直下のパスになる" {
            layoutOf {
                ".gitignore".file()
            }.shape() shouldBe listOf(".gitignore [File] required")
        }

        "ディレクトリの入れ子はルートからの相対パスに展開される" {
            layoutOf {
                "gradle" { "libs.versions.toml".file() }
            }.shape() shouldBe listOf(
                "gradle [Directory]",
                "gradle/libs.versions.toml [File] required",
            )
        }

        "複数階層のキーは1階層ずつのディレクトリに展開される" {
            layoutOf {
                "app/ios" { ignore() }
            }.shape() shouldBe listOf(
                "app [Directory]",
                "app/ios [Ignore]",
            )
        }

        "複数階層のファイルキーは途中がディレクトリ・末尾だけがファイルになる" {
            layoutOf {
                "gradle/libs.versions.toml".file()
            }.shape() shouldBe listOf(
                "gradle [Directory]",
                "gradle/libs.versions.toml [File] required",
            )
        }

        "空のディレクトリブロックはディレクトリ1件だけに展開される" {
            layoutOf {
                "di" { }
            }.shape() shouldBe listOf("di [Directory]")
        }
    }

    "ファイル指定" - {
        "file は名前をそのまま使う" {
            layoutOf { "libs.versions.toml".file() }.shape() shouldBe
                listOf("libs.versions.toml [File] required")
        }

        "ktFile は .kt を付ける" {
            layoutOf { "GetUserUseCase".ktFile() }.shape() shouldBe
                listOf("GetUserUseCase.kt [File] required")
        }

        "ktsFile は .kts を付ける" {
            layoutOf { "build.gradle".ktsFile() }.shape() shouldBe
                listOf("build.gradle.kts [File] required")
        }
    }

    "anyFile と ignore" - {
        "anyFile はディレクトリを AnyFile 種別にする" {
            layoutOf {
                "generated" { anyFile() }
            }.shape() shouldBe listOf("generated [AnyFile]")
        }

        "anyFile を書いたディレクトリにも明示のファイルを並べられる" {
            layoutOf {
                "generated" {
                    anyFile()
                    "README.md".file()
                }
            }.shape() shouldBe listOf(
                "generated [AnyFile]",
                "generated/README.md [File] required",
            )
        }

        "ブロック内の ignore とパスに対する ignore は同じ結果になる" {
            val inBlock = layoutOf { "build" { ignore() } }
            val onPath = layoutOf { "build".ignore() }

            inBlock.shape() shouldBe listOf("build [Ignore]")
            onPath.shape() shouldBe inBlock.shape()
        }

        "ignore は anyFile より強い" {
            layoutOf {
                "legacy" {
                    anyFile()
                    ignore()
                }
            }.shape() shouldBe listOf("legacy [Ignore]")
        }

        "ignore されたディレクトリは Missing の対象にならない" {
            layoutOf { "build".ignore() }.single().required shouldBe false
        }
    }

    "存在（required / optional）" - {
        "ワイルドカードを含まないファイルは必須になる" {
            layoutOf { "gradle/libs.versions.toml".file() }
                .single { it.kind == LayoutEntryKind.File }
                .required shouldBe true
        }

        "ワイルドカードを含むファイルは自動で optional になる" {
            layoutOf { "*UseCase".ktFile() }.single().required shouldBe false
        }

        "optional を付けたファイルは必須でなくなる" {
            layoutOf { "CHANGELOG.md".file().optional() }.single().required shouldBe false
        }

        "親ディレクトリのワイルドカードもファイルを optional にする" {
            layoutOf {
                "feature" / "*" / "README.md".file()
            }.single { it.kind == LayoutEntryKind.File }.required shouldBe false
        }

        "ディレクトリは必須にならない" {
            layoutOf {
                "gradle" { "libs.versions.toml".file() }
            }.single { it.kind == LayoutEntryKind.Directory }.required shouldBe false
        }

        "anyFile のディレクトリも必須にならない" {
            layoutOf { "generated" { anyFile() } }.single().required shouldBe false
        }
    }

    "description" - {
        "ディレクトリブロックの description が展開結果に載る" {
            layoutOf {
                "core/domain" {
                    description = "複数 feature から使われるもの"
                    "*UseCase".ktFile()
                }
            }.shape() shouldBe listOf(
                "core [Directory]",
                "core/domain [Directory] \"複数 feature から使われるもの\"",
                "core/domain/*UseCase.kt [File]",
            )
        }
    }

    "役割の対応づけ" - {
        "展開結果は宣言した役割を指す" {
            val entries = layoutOf { ".gitignore".file() }

            entries.single().role.qualifiedName shouldBe "group/Role"
        }

        "同じ役割が同じパスを2回宣言しても1件になる" {
            layoutOf {
                "src" / "main" / "A.kt".file()
                "src" / "main" / "B.kt".file()
            }.shape() shouldBe listOf(
                "src [Directory]",
                "src/main [Directory]",
                "src/main/A.kt [File] required",
                "src/main/B.kt [File] required",
            )
        }

        "別々の役割が同じパスを主張すると役割ごとに1件ずつ出る" {
            val entries = architecture {
                "domain".group {
                    "UseCase" { layout { "core/domain" { "*UseCase".ktFile() } } }
                    "Repository" { layout { "core/domain" { "*Repository".ktFile() } } }
                }
            }.flattenLayout()

            entries.filter { it.path == "core/domain" }.map { it.role.qualifiedName } shouldBe
                listOf("domain/UseCase", "domain/Repository")
        }

        "1つの役割が複数の layout ブロックを持てる" {
            val entries = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "core/domain" { "*UseCase".ktFile() } }
                        layout { "feature/*" { "*UseCase".ktFile() } }
                    }
                }
            }.flattenLayout()

            entries.map { it.path } shouldBe listOf(
                "core",
                "core/domain",
                "core/domain/*UseCase.kt",
                "feature",
                "feature/*",
                "feature/*/*UseCase.kt",
            )
        }

        "役割の展開結果は役割の宣言順に並ぶ" {
            val entries = architecture {
                "a".group { "First" { layout { "one.txt".file() } } }
                "b".group { "Second" { layout { "two.txt".file() } } }
            }.flattenLayout()

            entries.map { it.role.qualifiedName } shouldBe listOf("a/First", "b/Second")
        }
    }

    "宣言位置" - {
        "展開結果は利用者が書いた行を指す" {
            val entry = layoutOf { ".gitignore".file() }.single()

            entry.declaredAt.fileName shouldBe "LayoutFlatteningSpec.kt"
            entry.declaredAt.lineNumber shouldBeGreaterThan 0
        }
    }

    "壊れたキー" - {
        "空の階層を含むキーは読めないものとして弾かれる" {
            val failure = shouldThrow<GlobSyntaxException> {
                layoutOf { "app//ios" { } }
            }

            failure.message!! shouldContain "empty level"
        }

        "先頭のスラッシュも弾かれる" {
            shouldThrow<GlobSyntaxException> { layoutOf { "/app".file() } }
        }

        "他のツールの glob 構文は役割と宣言位置つきで弾かれる" {
            val failure = shouldThrow<GlobSyntaxException> {
                layoutOf { "{a,b}".file() }
            }

            failure.message!! shouldContain "group/Role"
            failure.message!! shouldContain "LayoutFlatteningSpec.kt"
        }
    }
})
