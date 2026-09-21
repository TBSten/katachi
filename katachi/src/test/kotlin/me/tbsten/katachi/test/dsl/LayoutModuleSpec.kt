package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.check.KatachiGlobSyntaxException
import me.tbsten.katachi.check.ModuleResolver
import me.tbsten.katachi.dsl.KatachiDeclarationException
import me.tbsten.katachi.dsl.KatachiModuleOutsideLayoutRootException
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.dsl.pascalCase

class LayoutModuleSpec : FreeSpec({
    "module は純粋な糖衣" - {
        "module の展開が手で書いたディレクトリブロックと一致する" {
            val sugar = layoutOf {
                ":x:y".module {
                    mainSourceSet / kotlin / "Foo".ktFile()
                }
            }
            val byHand = layoutOf {
                "x/y" {
                    "build".ignore()
                    "build.gradle".ktsFile()
                    "src" { "main" { "kotlin" { "Foo".ktFile() } } }
                }
            }

            sugar.shape() shouldBe byHand.shape()
            sugar.shape() shouldBe listOf(
                "x [Directory]",
                "x/y [Directory]",
                "x/y/build [Ignore]",
                "x/y/build.gradle.kts [File] required",
                "x/y/src [Directory]",
                "x/y/src/main [Directory]",
                "x/y/src/main/kotlin [Directory]",
                "x/y/src/main/kotlin/Foo.kt [File] required",
            )
        }

        "空の module ブロックは build の ignore と build.gradle.kts だけを展開する" {
            layoutOf { ":core:data".module { } }.shape() shouldBe listOf(
                "core [Directory]",
                "core/data [Directory]",
                "core/data/build [Ignore]",
                "core/data/build.gradle.kts [File] required",
            )
        }

        "module は src や proguard-rules.pro を展開しない" {
            val paths = layoutOf { ":core:data".module { } }.map { it.path }

            paths.contains("core/data/src") shouldBe false
            paths.contains("core/data/proguard-rules.pro") shouldBe false
            paths.contains("core/data/.gitignore") shouldBe false
        }

        "module ブロックに description を書ける" {
            layoutOf {
                ":core:data".module { description = "複数 feature から使われるもの" }
            }.shape() shouldBe listOf(
                "core [Directory]",
                "core/data [Directory] \"複数 feature から使われるもの\"",
                "core/data/build [Ignore]",
                "core/data/build.gradle.kts [File] required",
            )
        }

        "展開結果は利用者が書いた行を指す" {
            val entry = layoutOf { ":core:data".module { } }.first()

            entry.declaredAt.fileName shouldBe "LayoutModuleSpec.kt"
            entry.declaredAt.lineNumber shouldBeGreaterThan 0
        }
    }

    "モジュールパスの解決" - {
        "コロン区切りがそのままディレクトリ階層になる" {
            layoutOf { ":core:data:remoteApi".module { } }.map { it.path } shouldBe listOf(
                "core",
                "core/data",
                "core/data/remoteApi",
                "core/data/remoteApi/build",
                "core/data/remoteApi/build.gradle.kts",
            )
        }

        "先頭の : を省いても同じ結果になる" {
            layoutOf { "core:data".module { } }.shape() shouldBe
                layoutOf { ":core:data".module { } }.shape()
        }

        "ルートプロジェクトはプロジェクトルート直下に展開される" {
            layoutOf { ":".module { } }.shape() shouldBe listOf(
                "build [Ignore]",
                "build.gradle.kts [File] required",
            )
        }

        "moduleResolver を差し替えると別のディレクトリに展開される" {
            val resolver = ModuleResolver { module ->
                if (module.value == ":app") "apps/android" else module.segments.joinToString("/")
            }

            layoutOf(moduleIndexOf("apps/android", resolver = resolver)) {
                ":app".module { }
            }.map { it.path } shouldBe listOf(
                "apps",
                "apps/android",
                "apps/android/build",
                "apps/android/build.gradle.kts",
            )
        }

        "存在しないモジュールも build.gradle.kts を必須として宣言する" {
            layoutOf(moduleIndexOf("core/data")) { ":core:domain".module { } }
                .single { it.path == "core/domain/build.gradle.kts" }
                .required shouldBe true
        }
    }

    "ワイルドカードの展開" - {
        val features = moduleIndexOf("feature/home", "feature/debug-menu", "core/data")

        "マッチしたモジュールごとにブロックが1回ずつ評価される" {
            var evaluated = 0

            layoutOf(features) { ":feature:*".module { evaluated++ } }

            evaluated shouldBe 2
        }

        "捕捉値はマッチごとに入れ替わる" {
            val captured = mutableListOf<List<String>>()

            layoutOf(features) { ":feature:*".module { captured += wildcards } }

            captured shouldBe listOf(listOf("debug-menu"), listOf("home"))
        }

        "捕捉値から作ったファイル名がモジュールごとに変わる" {
            layoutOf(features) {
                ":feature:*".module {
                    "${wildcards[0].pascalCase}Screen".ktFile()
                }
            }.shape() shouldBe listOf(
                "feature [Directory]",
                "feature/debug-menu [Directory]",
                "feature/debug-menu/build [Ignore]",
                "feature/debug-menu/build.gradle.kts [File] required",
                "feature/debug-menu/DebugMenuScreen.kt [File] required",
                "feature/home [Directory]",
                "feature/home/build [Ignore]",
                "feature/home/build.gradle.kts [File] required",
                "feature/home/HomeScreen.kt [File] required",
            )
        }

        "\":core:*:*\" は階層ごとに1要素を捕捉する" {
            val captured = mutableListOf<List<String>>()

            layoutOf(moduleIndexOf("core/data/remoteApi")) {
                ":core:*:*".module { captured += wildcards }
            }

            captured shouldBe listOf(listOf("data", "remoteApi"))
        }

        "\"**\" は階層ごとに平坦化され、モジュール自身では空リストになる" {
            val captured = mutableListOf<List<String>>()

            layoutOf(moduleIndexOf("feature", "feature/hoge", "feature/hoge/fuga")) {
                ":feature:**".module { captured += wildcards }
            }

            captured shouldBe listOf(emptyList(), listOf("hoge"), listOf("hoge", "fuga"))
        }

        "マッチが0件なら何も宣言されず Missing も生まれない" {
            layoutOf(moduleIndexOf("core/data")) { ":feature:*".module { } } shouldBe emptyList()
        }

        "\"**\" を末尾以外に置くと評価時にエラーになる" {
            val failure = shouldThrow<KatachiGlobSyntaxException> {
                layoutOf(features) { ":feature:**:impl".module { } }
            }

            failure.message!! shouldContain "LayoutModuleSpec.kt"
        }

        "読めないモジュールパスは宣言位置つきで弾かれる" {
            val failure = shouldThrow<KatachiGlobSyntaxException> {
                layoutOf { ":core::data".module { } }
            }

            failure.message!! shouldContain "core::data"
            failure.message!! shouldContain "LayoutModuleSpec.kt"
        }
    }

    "存在（optional）" - {
        "optional を付けると配下のファイルが必須でなくなる" {
            layoutOf {
                ":experimental".module { "README.md".file() }.optional()
            }.shape() shouldBe listOf(
                "experimental [Directory]",
                "experimental/build [Ignore]",
                "experimental/build.gradle.kts [File]",
                "experimental/README.md [File]",
            )
        }

        "optional を付けても配下の宣言そのものは残る" {
            layoutOf {
                ":experimental".module { "README.md".file() }.optional()
            }.map { it.path } shouldBe listOf(
                "experimental",
                "experimental/build",
                "experimental/build.gradle.kts",
                "experimental/README.md",
            )
        }

        "optional は同じ layout の別の宣言に及ばない" {
            layoutOf {
                "gradle/libs.versions.toml".file()
                ":experimental".module { }.optional()
            }.single { it.path == "gradle/libs.versions.toml" }.required shouldBe true
        }
    }

    "書ける場所" - {
        "ディレクトリブロックの中には書けない" {
            val failure = shouldThrow<KatachiDeclarationException> {
                layoutOf { "app" { ":core:data".module { } } }
            }

            failure.shouldBeInstanceOf<KatachiModuleOutsideLayoutRootException>()
                .modulePath shouldBe ":core:data"
            failure.message!! shouldContain "layout { }"
        }

        "module の中に module は書けない" {
            shouldThrow<KatachiDeclarationException> {
                layoutOf { ":app".module { ":core:data".module { } } }
            }
        }

        "wildcards はモジュールの外では読めない" {
            val failure = shouldThrow<KatachiDeclarationException> {
                layoutOf { wildcards }
            }

            failure.shouldBeInstanceOf<KatachiWildcardsOutsideModuleException>()
            failure.message!! shouldContain "module { }"
        }
    }
})
