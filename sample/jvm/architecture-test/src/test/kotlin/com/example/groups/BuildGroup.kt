package com.example.groups

import com.example.roles.gradle
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the build definition.
 *
 * Build files are checked like everything else, but they are noise in the generated
 * documentation, so the whole group opts out. `documented` is a property written inside the
 * block, the same on a group as on a role; it is metadata like anything else a processor
 * reads, which is why it is not a parameter of `group()`.
 *
 * What the build *writes* needs no role at all: `build/` and `.kotlin/` are listed in
 * `.gitignore`, and the default `files = gitTracked()` never offers them to the check. Every
 * `.module { }` still says `"build".ignore()` out loud, because a project that chose
 * `files = wholeTree()` has no git to lean on and the reason has to be readable either way.
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

        ここに置いてはいけないのは、実行時に読む設定です。ポートやログの設定は
        アプリケーション層のサーバ設定の役割になります。
    """.trimIndent()

    gradle()
}
