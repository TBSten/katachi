package com.example.groups

import com.example.forbiddenContents
import com.example.roles.gradle
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the build definition.
 *
 * Build files are checked like everything else, but they are noise in the generated
 * documentation, so the whole group opts out. `RoleDocCoverage` reads the same flag and skips
 * the roles below it, which is why `build/Gradle` is not counted among the roles it checks.
 */
fun DeclarationContainerScope.buildGroup() = "build".group {
    documented = false
    title = "ビルド"
    summary = "ビルドの定義と Gradle wrapper。チェックはするがドキュメントには出さない"

    description = """
        ビルド時に効くものだけを集めた場所です。Gradle スクリプト、wrapper、version catalog が
        入ります。

        `documented = false` を付けているのは、これらが「アプリの構成」を読みに来た人にとって
        ノイズだからです。チェックからは外れていないので、`settings.gradle.kts` を消せば
        これまでどおり落ちます。

        このサンプルではもう1つ効きます。`RoleDocCoverage` は「ドキュメントに出る役割」だけを
        見るので、このグループの役割は対象外になります。metadata は継承されないため、
        group をたどって判断するのは processor 側の仕事です。
    """.trimIndent()

    forbiddenContents = "ここに置いてはいけないのは、実行時に読む設定です。"

    gradle()
}
