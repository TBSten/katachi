package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the flattened form of this definition, kept as text so a change to it is read
 * as a diff.
 *
 * The sibling of [generatedDocumentation]: both are written by a machine out of this very
 * definition and both are committed rather than left in `build/`, because a generated artifact
 * nobody can open is worth nothing. Outside `build/` means inside `files = gitTracked()`, which
 * is why each of them has a role.
 */
fun DeclarationContainerScope.layoutSnapshot() = "LayoutSnapshot" {
    title = "レイアウトのスナップショット"
    summary = "この定義を平坦化して全行書き出した記録。定義の変化を人が差分でレビューするためにある"
    description = """
        `:architecture-test:test` が、この定義を平坦化した結果を1行1エントリで書き出したものです。
        1行は `<役割の qualifiedName>` `<パス>` `<種別>` `<required|optional>` の4つ組で、
        `layout { }` が最終的に何を許しているのかがそのまま並びます。

        これは手で編集しない。`LayoutSnapshotSpec` が書きます。更新するには
        `./gradlew :architecture-test:test --rerun -Dkatachi.snapshot.update=true` を走らせるだけです
        （`--rerun` が要るのは、このシステムプロパティは `test` タスクの入力に配線してあるものの、
        他の入力が変わっていなければ Gradle が UP-TO-DATE で済ませてしまうからです）。

        何のためにあるのかというと、定義の変化を人が差分でレビューするためです。役割を
        `.module { }` や `mainSourceSet` で書き直したとき、読みやすくなっただけなのか、
        検査する木そのものが変わってしまったのかは、定義のコードを眺めても分かりません。
        平坦化した結果をコミット済みのテキストにしておけば、`git diff` の行数がそのまま答えに
        なります。1行も動かなければ書き換えは等価、動いたなら何がどう変わったのかがその行に
        出ています。

        これは katachi 自身の検証のために置いてあるもので、katachi を導入するときに書くもの
        ではありません。サンプルの定義に手を入れたときに意味が変わっていないことを、katachi の
        開発側が確かめるための記録です。

        生成物なのに `build/` の外に置いているのは、差分で見せるのが仕事だからです。
        コミットされていない記録は誰のレビューにも出てきません。その代わり既定の
        `files = gitTracked()` の検査対象に入るので、この役割が要ります。役割を消すと
        `[UnexpectedDirectory] snapshots` で `:architecture-test:test` が落ちます。

        `documented` は書いていません（既定の `true`）。この `snapshots/` が何なのかを説明する
        場所は他に無く、一覧から外すとリポジトリを開いた人には由来の分からない `.txt` が
        1つ残るだけになるからです。この役割自身のページ
        （`docs/testing/LayoutSnapshot.md`）も生成されます。
    """.trimIndent()
    forbiddenContents = """
        - 手書きのメモ。次の `-Dkatachi.snapshot.update=true` で丸ごと上書きされます
        - 別の種類の記録。いまの `layout { }` は `snapshots/layout.txt` の1ファイルしか
          認めていないので、足すなら役割を書き換えるところから始まります
    """.trimIndent()
    example("snapshots/layout.txt", "平坦化した layout の全行")
    layout {
        "snapshots" {
            // Written out by name rather than as `*.txt`, so the entry carries no wildcard
            // and is therefore `required`. A snapshot that was deleted is reported as
            // `[MissingFile]` here instead of passing as an empty allow list.
            "layout.txt".file()
        }
    }
}
