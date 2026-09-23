package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.test.architecture.testPackage

/** The role of test code that is not itself a test: fixtures, fakes, shared assembly. */
fun DeclarationContainerScope.specSupport() = "SpecSupport" {
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
