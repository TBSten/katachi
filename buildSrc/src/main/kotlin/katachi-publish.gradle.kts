// Maven Central へ出すモジュールの共通設定。
//
// 適用するのは `:katachi` と `:katachi-konsist` の2つだけ。`:architecture-test` は
// katachi 自身の検査用で、公開しない（この plugin を適用しなければ publish タスクも生えない）。
//
// 座標は me.tbsten.katachi。namespace は Central Portal で検証済み。
package buildsrc.convention

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    // 下の kotlin { } のアクセサを生やすために宣言している。実際に適用するのは
    // buildsrc.convention.kotlin-jvm 側で、ここでの宣言は二重適用にならない。
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
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

mavenPublishing {
    // Central は sources jar と javadoc jar の両方を必須にしている。
    // Kotlin には javadoc が無いので、Dokka の出力を javadoc jar として包む。
    //
    // タスク名は "dokkaGenerate" ではなく "dokkaGeneratePublicationJavadoc"。
    // "dokkaGenerate" は DokkaBasePlugin が作る素の lifecycle タスク（DokkaBaseTask）で、
    // 自身は @OutputDirectory を1つも持たない（dependsOn で個々の generatePublication
    // タスクを束ねているだけ）。JavadocJar.Dokka(taskName) は素朴に
    // `from(tasks.named(taskName))` するだけなので、"dokkaGenerate" を渡すと
    // 依存タスクは走るが jar の中身が空になる
    // （com.vanniktech.maven.publish.tasks.JavadocJar$Companion.dokkaJavadocJar のソースで確認済み）。
    // "dokkaGeneratePublicationJavadoc" は @OutputDirectory を持つ実体のタスクなので中身が入る。
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationJavadoc"), sourcesJar = true))

    publishToMavenCentral()

    // `publishToMavenLocal` で手元に出すときは署名しない。GPG 鍵が無い環境でも
    // ローカル検証ができるようにするため。CI の本番公開では必ず署名する。
    if (!providers.gradleProperty("katachi.skipSigning").isPresent) {
        signAllPublications()
    }

    pom {
        // artifactId はモジュール名がそのまま入る（katachi / katachi-konsist）。
        name.set(project.name)
        description.set(
            when (project.name) {
                "katachi-konsist" ->
                    "Konsist backend for katachi. Adds `konsist { }` so an architecture " +
                        "definition can constrain what a file declares, not just where it lives."
                else ->
                    "Declare your Android/KMP project architecture in a Kotlin DSL and check " +
                        "the whole tree against it, deny by default."
            },
        )
        inceptionYear.set("2026")
        url.set("https://github.com/TBSten/katachi")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/TBSten/katachi/blob/main/LICENSE")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("TBSten")
                name.set("tbsten")
                url.set("https://github.com/TBSten")
            }
        }

        scm {
            url.set("https://github.com/TBSten/katachi")
            connection.set("scm:git:git://github.com/TBSten/katachi.git")
            developerConnection.set("scm:git:ssh://git@github.com/TBSten/katachi.git")
        }
    }
}
