package me.tbsten.katachi.test.architecture.testing

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.sourceSet
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.test.architecture.KATACHI_TEST_PACKAGE
import me.tbsten.katachi.test.architecture.KONSIST_TEST_PACKAGE
import me.tbsten.katachi.test.architecture.KONTURE_TEST_PACKAGE

fun DeclarationContainerScope.specSupport() = "SpecSupport".role {
    title = "テストの道具"
    summary = "スペックではないテストコード。フィクスチャ、偽物のファイルシステム、共通の組み立て"
    example("FakeFileSystem.kt", "ディスクを触らずに走査を動かすための木")
    example("LayoutSpecSupport.kt", "layout の宣言を読み戻す共通の組み立て")
    example("FixtureProject.kt", "Konsist に読ませる実ファイルを一時ディレクトリに書き出す")
    example("SyntheticProject.kt", "性能を測るための大きな合成プロジェクトを種から決定的に作る")
    layout {
        "katachi" {
            description = "スペックが使う道具のうち、:katachi だけで組み立てられるもの。偽のファイルシステムや DSL の下ごしらえ"
            testSourceSet / kotlin / "**" / "*SpecSupport".ktFile()
            testSourceSet / kotlin / KATACHI_TEST_PACKAGE / "dsl" / "ArchitectureExtensions".ktFile()
            // The entry points and the written-at lines the specs of `definition-errors.json` share.
            testSourceSet / kotlin / KATACHI_TEST_PACKAGE / "processor" / "DefinitionErrorsSupport".ktFile()
            // Three of them — Fake / Forbidden / Throwing — and the glob stops short of
            // `FileSystemSpec.kt`, which is a spec and belongs to the role above.
            testSourceSet / kotlin / KATACHI_TEST_PACKAGE / "dsl" / "files" / "*FileSystem".ktFile()
            // The synthetic projects shared by the specs and the benchmarks, in a source set of
            // their own (`java-test-fixtures`) so that a module other than `:katachi` can use them.
            "testFixtures".sourceSet / kotlin / KATACHI_TEST_PACKAGE / "synthetic" / "Synthetic*".ktFile()
            "testFixtures".sourceSet / kotlin / KATACHI_TEST_PACKAGE / "synthetic" / "*FileSystem".ktFile()
            // What the SARIF specs read: the SARIF 2.1.0 JSON Schema the output is validated
            // against, the goldens `toSarif` is compared with, and the note saying where the
            // schema came from.
            testSourceSet / "resources" / "sarif" / "sarif-schema-2.1.0.json".file()
            testSourceSet / "resources" / "sarif" / "README.md".file()
            testSourceSet / "resources" / "sarif" / "golden" / "*.sarif".file()
            // The goldens `DescribeDefinition`'s JSON is compared with.
            testSourceSet / "resources" / "definition" / "golden" / "*.json".file()
        }
        "katachi-konsist" {
            description = "スペックが使う道具のうち、Konsist に読ませる実ファイルを一時ディレクトリに用意するもの"
            testSourceSet / kotlin / "**" / "*SpecSupport".ktFile()
            testSourceSet / kotlin / KONSIST_TEST_PACKAGE / "FixtureProject".ktFile()
            // The SARIF a failing `ktFileContent { }` writes, as `KtFileContentSarifSpec` compares it. The IDE plugin keeps a
            // copy as its contract (`katachi-intellij-plugin/src/test/resources/contract/json/`): its annotations are read against it.
            testSourceSet / "resources" / "sarif" / "*.sarif".file()
        }
        "katachi-konture" {
            description = "スペックが使う道具のうち、Konture に読ませる実ファイルを一時ディレクトリに用意するもの"
            testSourceSet / kotlin / "**" / "*SpecSupport".ktFile()
            testSourceSet / kotlin / KONTURE_TEST_PACKAGE / "FixtureProject".ktFile()
        }
        "katachi-script" {
            description = "スペックが使う道具のうち、作業用ディレクトリの形を一時ディレクトリに作るもの"
            testSourceSet / kotlin / "**" / "*SpecSupport".ktFile()
        }
        "tool/dokka" {
            description = "Dokka プラグインのスペックが使う道具。Dokka の実行の組み立てと、@featured を付けたインラインのソース"
            testSourceSet / kotlin / "**" / "*SpecSupport".ktFile()
        }
    }
}
