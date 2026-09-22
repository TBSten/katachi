package me.tbsten.katachi.test.architecture.library

import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.mainPackage

/**
 * The roles of `:katachi`, the library itself.
 *
 * Here a role is a layer, and that is an observation rather than a shortcut: in this
 * repository the kinds of file and the package layers happen to coincide. A file in `scan/`
 * is a piece of the traversal and cannot be anything else; the same holds for `fs/`, for
 * `processor/` and for `check/`. Where the two came apart, the role follows the kind and not
 * the package — see `Marker` and `LayoutVocabulary` below.
 *
 * Each role carries the same three `konsist { }` rules, in the same order.
 *
 * 1. **The layers it may not import.** Together these are what
 *    `katachi/src/test/kotlin/me/tbsten/katachi/test/PackageDependencySpec.kt` used to do by
 *    reading the sources as text. The forbidden list is built from one table, in
 *    `LayerImports.kt`, so the seven rules cannot drift apart.
 * 2. **That the package matches the directory** — `PACKAGE_MATCHES_PATH_RULE`, which is what
 *    keeps rule 1 meaning anything at all. See its own KDoc.
 * 3. **That every public declaration shows an example** — `KDOC_EXAMPLE_RULE`, the repository's
 *    KDoc convention. See `KdocExamples.kt`.
 *
 * ## Why every role repeats the same lines
 *
 * A helper that wrote `konsist { }` for a role would be captured as the declaration site of all
 * seven constraints, and every violation would point at the helper instead of at the role.
 * Only the predicates and the wording are shared; the declaration stays where it belongs.
 */
fun ArchitectureScope.libraryRoles() {
    "library".group {
        title = "ライブラリ"

        "Marker" {
            title = "マーカーと例外基底"
            summary = "どの層にも属さず、すべての層が依存してよいもの"
            example("ExperimentalKatachiApi.kt", "まだ形が動く API の opt-in マーカー")
            example("InternalKatachiApi.kt", "ライブラリ内部で共有するための opt-in マーカー")
            example("Exceptions.kt", "利用者が catch する例外の基底")
            layout {
                ":katachi".module {
                    "${laterLayersOf("")} を import しないこと".konsist {
                        files.mustNot(importsLaterLayerThan(""))
                    }
                    PACKAGE_MATCHES_PATH_RULE.konsist {
                        packages.must { it.hasMatchingPath }
                    }
                    KDOC_EXAMPLE_RULE.konsist {
                        files.flatMap(::publicDeclarationsOf).must(::showsExample)
                    }
                    // The root package, and only it: `*` never crosses a `/`, so the layer
                    // directories one level down are untouched by this.
                    mainSourceSet / kotlin / mainPackage / "*".ktFile()
                }
            }
        }

        "FileSystem" {
            title = "ファイルシステム"
            summary = "プロジェクトルートの発見と、走査するファイル集合の選択"
            example("KatachiFileSystem.kt", "走査が触る最小のファイルシステム抽象")
            example("GitTrackedFileSystem.kt", "git の管理下にあるファイルだけを見せる実装")
            layout {
                ":katachi".module {
                    "${laterLayersOf("fs")} を import しないこと".konsist {
                        files.mustNot(importsLaterLayerThan("fs"))
                    }
                    PACKAGE_MATCHES_PATH_RULE.konsist {
                        packages.must { it.hasMatchingPath }
                    }
                    KDOC_EXAMPLE_RULE.konsist {
                        files.flatMap(::publicDeclarationsOf).must(::showsExample)
                    }
                    mainSourceSet / kotlin / mainPackage / "fs" / "*".ktFile()
                    mainSourceSet / kotlin / mainPackage / "fs" / "**" / "*".ktFile()
                }
            }
        }

        "Dsl" {
            title = "DSL"
            summary = "architecture { } / group { } / role { } / layout { } の受け皿と、宣言されたモデル"
            description = """
                `dsl.gradle` と `dsl.kotlin` もこの層です。`layout { }` の上に context parameter で
                書かれた語彙で、種類としては別物ですが依存としては同じ層にいます。
                `dsl/LayoutScopeImpl.kt` が `dsl.kotlin.ktsFile` を import しているので
                （`.module { }` が `build.gradle.kts` を注入するため）、
                「コアは語彙を import しない」という規則は今日の時点で落ちます。
            """.trimIndent()
            example("LayoutScope.kt", "layout { } の受け皿。コアの語彙はこれで全部")
            example("Architecture.kt", "宣言し終わった1つの定義")
            example("Modules.kt", "\":core:data\".module { } — dsl.gradle の語彙")
            layout {
                ":katachi".module {
                    "${laterLayersOf("dsl")} を import しないこと".konsist {
                        files.mustNot(importsLaterLayerThan("dsl"))
                    }
                    PACKAGE_MATCHES_PATH_RULE.konsist {
                        packages.must { it.hasMatchingPath }
                    }
                    KDOC_EXAMPLE_RULE.konsist {
                        files.flatMap(::publicDeclarationsOf).must(::showsExample)
                    }
                    mainSourceSet / kotlin / mainPackage / "dsl" / "*".ktFile()
                    mainSourceSet / kotlin / mainPackage / "dsl" / "**" / "*".ktFile()
                }
            }
        }

        "Scan" {
            title = "走査"
            summary = "宣言に導かれてツリーを1度だけ歩き、違反と「役割ごとのファイル」を同時に出す"
            example("Scan.kt", "唯一の走査")
            example("Violation.kt", "報告される1件")
            layout {
                ":katachi".module {
                    "${laterLayersOf("scan")} を import しないこと".konsist {
                        files.mustNot(importsLaterLayerThan("scan"))
                    }
                    PACKAGE_MATCHES_PATH_RULE.konsist {
                        packages.must { it.hasMatchingPath }
                    }
                    KDOC_EXAMPLE_RULE.konsist {
                        files.flatMap(::publicDeclarationsOf).must(::showsExample)
                    }
                    mainSourceSet / kotlin / mainPackage / "scan" / "*".ktFile()
                    mainSourceSet / kotlin / mainPackage / "scan" / "**" / "*".ktFile()
                }
            }
        }

        "Processor" {
            title = "プロセッサ"
            summary = "1度の走査の結果を受け取って、好きな形に変換する入口"
            example("ProjectModel.kt", "走査を高々1度に抑えたうえで宣言と実体の両方を見せるモデル")
            example("ArchitectureProcessor.kt", "利用者が実装する変換")
            layout {
                ":katachi".module {
                    "${laterLayersOf("processor")} を import しないこと".konsist {
                        files.mustNot(importsLaterLayerThan("processor"))
                    }
                    PACKAGE_MATCHES_PATH_RULE.konsist {
                        packages.must { it.hasMatchingPath }
                    }
                    KDOC_EXAMPLE_RULE.konsist {
                        files.flatMap(::publicDeclarationsOf).must(::showsExample)
                    }
                    mainSourceSet / kotlin / mainPackage / "processor" / "*".ktFile()
                    mainSourceSet / kotlin / mainPackage / "processor" / "**" / "*".ktFile()
                }
            }
        }

        "Check" {
            title = "検査"
            summary = "利用者が呼ぶ assert() と、その報告の組み立て"
            example("Assert.kt", "利用者がテストに書く1行")
            example("ViolationReport.kt", "違反1件を人が読める1ブロックにする")
            layout {
                ":katachi".module {
                    "${laterLayersOf("check")} を import しないこと".konsist {
                        files.mustNot(importsLaterLayerThan("check"))
                    }
                    PACKAGE_MATCHES_PATH_RULE.konsist {
                        packages.must { it.hasMatchingPath }
                    }
                    KDOC_EXAMPLE_RULE.konsist {
                        files.flatMap(::publicDeclarationsOf).must(::showsExample)
                    }
                    mainSourceSet / kotlin / mainPackage / "check" / "*".ktFile()
                    mainSourceSet / kotlin / mainPackage / "check" / "**" / "*".ktFile()
                }
            }
        }
    }
}
