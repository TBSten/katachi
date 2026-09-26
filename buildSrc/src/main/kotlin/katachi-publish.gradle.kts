// Maven Central へ出すモジュールの共通設定。
//
// 適用するのは `:katachi` / `:katachi-konsist` / `:katachi-gradle-plugin` の3つ。
// `:architecture-test` は katachi 自身の検査用で、公開しない
// （この plugin を適用しなければ publish タスクも生えない）。
//
// **Kotlin 固有の設定はここには無い。** languageVersion 2.2 と Dokka は
// `buildsrc.convention.katachi-kotlin-library` 側にあり、Kotlin の2モジュールだけが適用する。
// `:katachi-gradle-plugin` は Java だけで書かれていて、Kotlin plugin が付くと
// kotlin-stdlib が POM に載ってしまうため。
//
// 座標は me.tbsten.katachi。namespace は Central Portal で検証済み。
package buildsrc.convention

import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    // Central は sources jar と javadoc jar の両方を必須にしている。
    //
    // どの platform になるかはモジュールによって違う。`:katachi` と `:katachi-konsist` は
    // 素の Kotlin/JVM ライブラリ。`:katachi-gradle-plugin` は `java-gradle-plugin` を適用して
    // いて、あちらが同じ `java` component から `pluginMaven` publication を、さらに登録した
    // plugin id ごとに `<name>PluginMarkerMaven` を作る。ここで `KotlinJvm` を選ぶと
    // `maven` という **2つ目の** publication が同じ座標で生え、両方がアップロードされる。
    // `GradlePlugin` なら `java-gradle-plugin` が作った publication を装飾するだけで、
    // marker は除外される（GradlePlugin.configure -> mavenPublicationsWithoutPluginMarker）。
    //
    // この判定は convention の適用時に走るので、モジュール側の plugins { } では
    // `java-gradle-plugin` を **この convention より先に** 書くこと。下の afterEvaluate が
    // 書き間違いを声に出して落とす。
    if (project.plugins.hasPlugin("java-gradle-plugin")) {
        // Java しか持たないモジュールなので javadoc は Dokka ではなく素の javadoc タスク。
        configure(GradlePlugin(javadocJar = JavadocJar.Javadoc(), sourcesJar = SourcesJar.Sources()))
    } else {
        // タスク名は "dokkaGenerate" ではなく "dokkaGeneratePublicationJavadoc"。
        // "dokkaGenerate" は DokkaBasePlugin が作る素の lifecycle タスク（DokkaBaseTask）で、
        // 自身は @OutputDirectory を1つも持たない（dependsOn で個々の generatePublication
        // タスクを束ねているだけ）。JavadocJar.Dokka(taskName) は素朴に
        // `from(tasks.named(taskName))` するだけなので、"dokkaGenerate" を渡すと
        // 依存タスクは走るが jar の中身が空になる
        // （com.vanniktech.maven.publish.tasks.JavadocJar$Companion.dokkaJavadocJar のソースで確認済み）。
        // "dokkaGeneratePublicationJavadoc" は @OutputDirectory を持つ実体のタスクなので中身が入る。
        //
        // sourcesJar に Boolean を渡す overload は 0.37.0 で deprecated。
        // `SourcesJar.Sources()` は primary constructor の既定値と同じ意味。
        configure(
            KotlinJvm(
                javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationJavadoc"),
                sourcesJar = SourcesJar.Sources(),
            ),
        )
    }

    publishToMavenCentral()

    // `publishToMavenLocal` で手元に出すときは署名しない。GPG 鍵が無い環境でも
    // ローカル検証ができるようにするため。CI の本番公開では必ず署名する。
    if (!providers.gradleProperty("katachi.skipSigning").isPresent) {
        signAllPublications()
    }

    pom {
        // artifactId はモジュール名がそのまま入る
        // （katachi / katachi-konsist / katachi-gradle-plugin）。
        // plugin marker だけは me.tbsten.katachi:me.tbsten.katachi.gradle.plugin という
        // 別座標になるが、name / description / licenses / developers / scm はこの pom { } が
        // 全 publication に配るので marker にも入る。
        name.set(project.name)
        description.set(
            when (project.name) {
                "katachi-konsist" ->
                    "Konsist backend for katachi. Adds `konsist { }` so an architecture " +
                        "definition can constrain what a file declares, not just where it lives."

                "katachi-gradle-plugin" ->
                    "Gradle plugin for katachi. Registers one task per katachi processor " +
                        "(katachiDocs, katachiTemplate, ...), each running it against the " +
                        "architecture definition on the module's test runtime classpath."

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

// `java-gradle-plugin` をこの convention より後ろに書くと、上の分岐は静かに KotlinJvm 側へ
// 落ちて `maven` と `pluginMaven` が同じ座標で2つ生える。Central へ2回上がるまで誰も
// 気づけないので、configure 時に落とす。
//
// configure() 自体を afterEvaluate へ遅らせる案は採れない。vanniktech 自身の afterEvaluate が
// 先に登録されていて platform を finalize するため、後から configure() すると例外になる。
afterEvaluate {
    val hasPluginDevelopment = project.plugins.hasPlugin("java-gradle-plugin")
    val publishing = extensions.getByType(PublishingExtension::class.java)
    val hasKotlinJvmPublication = publishing.publications.findByName("maven") != null
    check(!hasPluginDevelopment || !hasKotlinJvmPublication) {
        "${project.path}: apply `java-gradle-plugin` before " +
            "`buildsrc.convention.katachi-publish`. Applied the other way round this module " +
            "publishes the same coordinates twice, as `maven` and as `pluginMaven`."
    }
}
