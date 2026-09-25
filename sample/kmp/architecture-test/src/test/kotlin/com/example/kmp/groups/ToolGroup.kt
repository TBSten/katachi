package com.example.kmp.groups

import com.example.kmp.roles.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Files that belong to the tools around the project rather than to the build. Split out of
 * the `build` group so that "how this project is built" and "what tooling it carries" do not
 * share one bucket.
 *
 * Undocumented for the same reason the build group is: real, but not part of the
 * architecture a reader of the generated docs is looking for.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "ツール"
    summary = "ビルドではなく、プロジェクトの周りの道具に属するファイル"
    description = """
        プロジェクトの周りにある道具の設定です。今は `.gitignore` 1つだけなので、build group に
        入れてしまっても動きはします。それでも分けているのは、「このプロジェクトをどうビルドするか」
        と「このプロジェクトがどんな道具を持っているか」を1つのバケツに入れたくないからです。
        道具は種類が増える一方で、増えたときに分けるのは分けておくより面倒です。

        ここに来るもの:

        - git に伝える設定（`.gitignore`）
        - 将来足すとすれば linter や formatter の設定、`.editorconfig` のようなもの

        ここに来ないもの:

        - ビルドファイル。`build.gradle.kts` や wrapper は build group です
        - CI の設定。このサンプルは自前の `.github/` を持たず、リポジトリルートの
          ワークフローから回されています

        build group と同じく `documented = false` です。実在するけれど、生成ドキュメントの
        読者が探しているアーキテクチャではありません。
    """.trimIndent()

    git()
}
