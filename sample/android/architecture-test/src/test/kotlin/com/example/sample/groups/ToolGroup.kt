package com.example.sample.groups

import com.example.sample.roles.documentation
import com.example.sample.roles.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the tools a repository carries that are neither the app nor its build: git and
 * the prose that explains the sample, plus whatever else earns a place later (CI,
 * formatters, editor settings).
 *
 * They sit in their own group rather than in `build`, because a group is declared exactly
 * once and each of these files belongs to a different tool. Like the build scripts, they
 * are checked but not documented.
 *
 * This group is also what `ProjectArchitectureSpec` leaves out to prove the check is not
 * passing by accident: drop it and exactly the two files it covers turn into violations.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "ツール"
    summary = "アプリでもビルドでもない、リポジトリが抱えている道具"
    description = """
        リポジトリのルートに置かれる、アプリでもビルド設定でもないファイル。いまは git が読む
        `.gitignore` と、人が読む `README.md` の2つ。CI の設定やフォーマッタの設定が増えたら、
        役割を足してここに入れる。

        `build` group とは別にしてある。group は1度しか宣言できないのに対し、ここにあるのは
        それぞれ別の道具のファイルなので、「ビルド」とひとまとめにすると次のファイルを
        どの役割に足すべきかが分からなくなる。

        `documented = false`。ビルド設定と同じく、このアプリが何であるかを語らないため。

        この group は、検査が素通りしていないことの証明にも使われている。
        `ProjectArchitectureSpec` は `toolGroup()` だけを外した定義を組み、`.gitignore` と
        `README.md` のちょうど2件が `[UnexpectedFile]` になることを確かめる。group を外しても
        違反が0件なら、検査は何も歩いていないことになる。
    """.trimIndent()

    git()
    documentation()
}
