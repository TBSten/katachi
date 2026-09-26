package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the pages `katachiDocs` writes out of this very definition.
 *
 * A generated artifact that lives outside `build/` needs a role like anything else: the default
 * `files = gitTracked()` offers it to the check, and a directory no role claims is a violation.
 * Declaring it is what buys the pages a place in the repository, where a reader can find them.
 */
fun DeclarationContainerScope.generatedDocumentation() = "GeneratedDocumentation" {
    title = "生成ドキュメント"
    summary = "この定義から書き出され、リポジトリにコミットされる Markdown"
    description = """
        `./gradlew :architecture-test:katachiDocs` が、この定義そのものから
        書き出す Markdown です。出力先は `katachi { processors { docs { outputDir } } }` で
        `sample/jvm/docs` に向けてあります。

        これは手で書かない。`katachiDocs` が書く。ここに書き足した文章は次の生成で消えます。
        直す場所は常に定義側で、役割や group の `title` `summary` `description` `example` が
        そのままページになります。

        生成物なのに `build/` の外に置いているのは、リポジトリを開いた人がそのまま読めるように
        するためです。その代わり既定の `files = gitTracked()` の検査対象に入るので、この役割が
        要ります。役割を消すと `[UnexpectedDirectory] docs` で `:architecture-test:test` が落ちます。

        古くなっていないかは CI が `--arg mode=check` で見ています。`mode=check` は何も書かずに
        ディスク上の内容と突き合わせ、食い違えば例外で落ちるので、定義を変えて生成し忘れたまま
        push するとリポジトリルートの `checkSampleJvm` が赤くなります。手元で直すには
        `katachiDocs` をもう一度走らせるだけです（`mode` を付けなければ書き込みです）。

        `documented = true` にしてあります。生成物であっても一覧に出ないと、この `docs/` が
        何なのかがどこにも書かれていないことになるからです。この役割自身のページ
        （`docs/testing/GeneratedDocumentation.md`）も生成されます。

        `layout { }` の `**` は0段以上に一致するので、索引の `docs/README.md` も
        `docs/<group>/README.md` も `docs/<group>/<役割>.md` も1行で覆えます。group を入れ子に
        してもこの行は変わりません。索引だけワイルドカード無しで別に書いてあり、そちらは
        `required` です。1度も生成していない状態がそこで見つかります。
    """.trimIndent()
    forbiddenContents = """
        - 手書きのドキュメント。`docs/` 配下の `*.md` は生成のたびに作り直され、この定義が
          作らないページは削除されます。人が書く散文は `.gitignore` と並ぶ `tool` グループの側です
        - `.md` 以外の資源。いまの `layout { }` は `*.md` しか認めていないので、画像を足すなら
          役割を書き換えるところから始まります
    """.trimIndent()
    example("docs/README.md", "全ページの索引と、group ごとの一覧")
    example("docs/api/Controller.md", "役割1つのページ")
    example("docs/api/README.md", "group 1つのページ")
    layout {
        "docs" {
            // The index, written on every run, so it is the one entry without a wildcard --
            // and therefore the one entry that is `required`. A `docs/` that was never
            // generated is reported here rather than passing as an empty allow list.
            "README.md".file()
            // `**` matches zero levels or more, so this one line covers the index, every
            // group's `README.md` and every role's page -- and keeps covering them if a
            // group is ever nested inside another.
            "**" / "*.md".file()
        }
    }
}
