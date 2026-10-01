package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of a test that confirms one behaviour.
 *
 * It reaches across every module that has tests, `:architecture-test` included. That module checks itself
 * like everything else — the price of the recommended setup, and the point of it.
 */
fun DeclarationContainerScope.spec() = "Spec" {
    title = "スペック"
    summary = "kotest の FreeSpec。振る舞いを1つ確かめる"
    example("ScanSpec.kt", "走査そのものを確かめる")
    example("ProjectArchitectureSpec.kt", "この定義でリポジトリ全体が宣言しきれていることを確かめる")
    layout {
        // `**` stands for the package levels below the source set. They mirror the
        // production packages and are not worth writing twice, so the `*_TEST_PACKAGE`
        // constants are deliberately not used here — unlike in `SpecSupport`, which names single
        // files and therefore has to say where they are.
        "katachi" {
            description = "ライブラリ本体の振る舞い。偽のファイルシステムで完結し、Konsist も Gradle も要らないもの"
            testSourceSet / kotlin / "**" / "*Spec".ktFile()
        }
        "katachi-konsist" {
            description = "Konsist 連携の振る舞い。実ファイルを書き出して Konsist に読ませる必要があるもの"
            testSourceSet / kotlin / "**" / "*Spec".ktFile()
        }
        "architecture-test" {
            description = "katachi を利用者として使う側。このリポジトリ自身の定義について確かめるもの"
            testSourceSet / kotlin / "**" / "*Spec".ktFile()
        }
        "tool/dokka" {
            description = "API リファレンス用の Dokka プラグインの振る舞い。インラインのソースから Dokka を実際に走らせて確かめるもの"
            testSourceSet / kotlin / "**" / "*Spec".ktFile()
        }
    }
}
