// Maven Central へ出す **Kotlin の** ライブラリに共通する設定。
//
// 適用するのは `:katachi` と `:katachi-konsist` の2つ。3つ目の公開モジュール
// `:katachi-gradle-plugin` は Java だけで書かれていて、Kotlin の互換設定も Dokka も要らない
// （理由は katachi-gradle-plugin/build.gradle.kts の冒頭）。だからここは
// `katachi-publish` から切り出してある。
//
// **`katachi-publish` より先に適用すること。** あちらの javadoc jar は、ここで生える
// `dokkaGeneratePublicationJavadoc` タスクの出力を包む。
package buildsrc.convention

import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    // 下の kotlin { } のアクセサを生やすために宣言している。実際に適用するのは
    // buildsrc.convention.kotlin-jvm 側で、ここでの宣言は二重適用にならない。
    kotlin("jvm")
    // HTML 出力（ルートの集約用に module を作る）と Javadoc 出力（javadoc jar 用）は
    // Dokka 2.x では別 plugin。`org.jetbrains.dokka` は HTML format を自動適用するのみで、
    // Javadoc format は `org.jetbrains.dokka-javadoc` を別途適用しないと生えない
    // （org.jetbrains.dokka.gradle.formats.DokkaJavadocPlugin のソースで確認済み）。
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
}

dokka {
    // モジュール表示名。デフォルトは project.name と同じだが、明示しておく
    // （:katachi -> "katachi", :katachi-konsist -> "katachi-konsist"）。
    moduleName.set(project.name)

    dokkaSourceSets.configureEach {
        jdkVersion.set(17)

        // Kotlin stdlib への外部リンクは DGPv2 のデフォルトで有効
        // （DokkaBasePlugin が enableKotlinStdLibDocumentationLink.convention(true) を設定する）。
        // ここで明示的に externalDocumentationLinks を足す必要はない。

        sourceLink {
            // localDirectory は既定で layout.projectDirectory（このモジュールのルート）なので、
            // Dokka がソースファイルへの相対パスを自動で remoteUrl の後ろに付け、
            // remoteLineSuffix（既定 "#L"）で行番号を付ける。
            remoteUrl("https://github.com/TBSten/katachi/blob/main/${project.name}")
        }
    }
}

// 公開する成果物は **Kotlin 2.2 のコンパイラが読める metadata** で出す。
//
// 既定のままだと metadata は mv=[2,4] になり、Kotlin 2.2 のプロジェクトでは
// katachi のシンボルがすべて Unresolved reference になる（2.2 が読めるのは 2.3 まで）。
// 実際に Kotlin 2.2.20 のプロジェクトへ導入しようとして詰まった報告があった。
//
// languageVersion を下げると context parameters が preview 扱いに戻るので、
// katachi 自身のビルドに -Xcontext-parameters が要る。DSL の入口
// （module / sourceSet / ktFile / konsist …）はすべて context parameters なので、
// これを外すと katachi がコンパイルできない。
//
// coreLibrariesVersion も下げる。下げないと推移的に入る kotlin-stdlib が
// 2.4 系になり、そちらの metadata で同じ問題が起きる。
//
// **代償**: katachi 自身が Kotlin 2.2 にある言語機能しか使えなくなる。
// 下限を上げるときは README と katachi-install.sh の KOTLIN_MIN_* も一緒に動かすこと。
kotlin {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
    coreLibrariesVersion = "2.2.20"
}
