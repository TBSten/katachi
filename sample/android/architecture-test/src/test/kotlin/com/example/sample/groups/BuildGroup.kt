package com.example.sample.groups

import com.example.sample.roles.gradleModule
import com.example.sample.roles.gradleRoot
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the build setup: the Gradle scripts that describe how this project is assembled.
 *
 * Build files are checked but not documented: they are the same in every project and say
 * nothing about this app. `documented` is not inherited (a declared value is kept as
 * written), so each role also has to say `documented = false` for itself.
 *
 * The build's *output* needs no declaration of its own. `build/` is ignored by `.gitignore`
 * and the default `files = gitTracked()` never offers it to the check; the `build` that
 * `.module { }` adds is what makes the definition say so out loud rather than rely on it.
 */
fun DeclarationContainerScope.buildGroup() = "build".group {
    documented = false
    title = "ビルド"
    summary = "このプロジェクトを1つの Gradle ビルドにまとめているファイル"
    description = """
        Gradle のビルドスクリプト。モジュールごとの `build.gradle.kts` と、それらを1つの
        ビルドにまとめるルート側のファイル（`settings.gradle.kts` / `gradle.properties` /
        wrapper / バージョンカタログ）の2つに分けてある。どのモジュールがあるかを言うのが前者、
        それらが1つのビルドであることを言うのが後者。

        `documented = false`。ビルド設定はどのプロジェクトにもある同じ形で、このアプリが
        何であるかを何も語らないので、生成されるドキュメントには出さない。検査からは外れない。
        `documented` は継承されないので、この group の中の役割もそれぞれ自分で
        `documented = false` と書いている。

        ビルドの**出力**には役割が要らない。`build/` は `.gitignore` が無視し、既定の
        `files = gitTracked()` が最初から検査対象に含めない。`.module { }` が付ける
        `"build".ignore()` は、その前提に黙って頼らず定義の中で言い切るためにある。
    """.trimIndent()

    gradleModule()
    gradleRoot()
}
