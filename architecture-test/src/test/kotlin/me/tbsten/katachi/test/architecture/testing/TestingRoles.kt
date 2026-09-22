package me.tbsten.katachi.test.architecture.testing

import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.test.architecture.testPackage

/**
 * The roles of the test code, including the architecture definition itself.
 *
 * ## Why the tests are not split by layer
 *
 * The test sources of `:katachi` mirror the production packages — `fs`, `dsl`, `check` — so
 * layers would have been the easy split. They would also have said nothing: a test may import
 * anything it likes, so there is no direction to enforce and no file that would land in the
 * wrong one. The boundary that is real here is a different one, and it is written in the file
 * names: a test (`*Spec.kt`) and the scaffolding a test uses. So these roles are cut by name.
 *
 * `Spec` reaches across all three modules, this one included. `:architecture-test` checks
 * itself like everything else — that is the price of the recommended setup, and the point of
 * it.
 */
fun ArchitectureScope.testingRoles() {
    "testing".group {
        title = "テスト"

        "Spec" {
            title = "スペック"
            summary = "kotest の FreeSpec。振る舞いを1つ確かめる"
            example("ScanSpec.kt", "走査そのものを確かめる")
            example("ProjectArchitectureSpec.kt", "この定義でリポジトリ全体が宣言しきれていることを確かめる")
            layout {
                // `**` stands for the package levels below the source set. They mirror the
                // production packages and are not worth writing twice, so `testPackage` is
                // deliberately not used here — unlike in `SpecSupport`, which names single
                // files and therefore has to say where they are.
                ":katachi".module {
                    description = "ライブラリ本体の振る舞い。偽のファイルシステムで完結し、Konsist も Gradle も要らないもの"
                    testSourceSet / kotlin / "**" / "*Spec".ktFile()
                }
                ":katachi-konsist".module {
                    description = "Konsist 連携の振る舞い。実ファイルを書き出して Konsist に読ませる必要があるもの"
                    testSourceSet / kotlin / "**" / "*Spec".ktFile()
                }
                ":architecture-test".module {
                    description = "katachi を利用者として使う側。このリポジトリ自身の定義について確かめるもの"
                    testSourceSet / kotlin / "**" / "*Spec".ktFile()
                }
            }
        }

        "SpecSupport" {
            title = "テストの道具"
            summary = "スペックではないテストコード。フィクスチャ、偽物のファイルシステム、共通の組み立て"
            example("FakeFileSystem.kt", "ディスクを触らずに走査を動かすための木")
            example("LayoutSpecSupport.kt", "layout の宣言を読み戻す共通の組み立て")
            example("FixtureProject.kt", "Konsist に読ませる実ファイルを一時ディレクトリに書き出す")
            layout {
                ":katachi".module {
                    description = "スペックが使う道具のうち、:katachi だけで組み立てられるもの。偽のファイルシステムや DSL の下ごしらえ"
                    testSourceSet / kotlin / "**" / "*SpecSupport".ktFile()
                    testSourceSet / kotlin / testPackage / "dsl" / "ArchitectureExtensions".ktFile()
                    // Three of them — Fake / Forbidden / Throwing — and the glob stops short of
                    // `FileSystemSpec.kt`, which is a spec and belongs to the role above.
                    testSourceSet / kotlin / testPackage / "fs" / "*FileSystem".ktFile()
                }
                ":katachi-konsist".module {
                    description = "スペックが使う道具のうち、Konsist に読ませる実ファイルを一時ディレクトリに用意するもの"
                    testSourceSet / kotlin / "**" / "*SpecSupport".ktFile()
                    testSourceSet / kotlin / testPackage / "FixtureProject".ktFile()
                }
            }
        }

        "ArchitectureDefinition" {
            title = "アーキテクチャ定義"
            summary = "katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない"
            example("ProjectArchitecture.kt", "定義の入口。各 package の拡張関数を呼ぶ")
            example("LibraryRoles.kt", "ライブラリ本体の7役割を宣言する拡張関数")
            layout {
                ":architecture-test".module {
                    testSourceSet / kotlin / testPackage / "ProjectArchitecture".ktFile()
                    // One package per concern, each exposing `ArchitectureScope` extensions.
                    // The glob is on the file name, so splitting a group's roles across two
                    // files needs no change here as long as both end in `Roles.kt`.
                    testSourceSet / kotlin / testPackage / "*" / "*Roles".ktFile()
                    testSourceSet / kotlin / testPackage / "library" / "LayerImports".ktFile()
                    testSourceSet / kotlin / testPackage / "library" / "KdocExamples".ktFile()
                }
            }
        }
    }
}
