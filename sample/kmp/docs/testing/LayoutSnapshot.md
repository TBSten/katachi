[katachi-sample-kmp](../README.md) / [テスト支援](README.md)

# レイアウトのスナップショット

この定義を平坦化して全行書き出した記録。定義の変化を人が差分でレビューするためにある

`:architecture-test:test` が、この定義を平坦化した結果を1行1エントリで書き出したものです。
1行は `<役割の qualifiedName>` `<パス>` `<種別>` `<required|optional>` の4つ組で、
`layout { }` が最終的に何を許しているのかがそのまま並びます。

これは手で編集しない。`LayoutSnapshotSpec` が書きます。更新するには
`./gradlew :architecture-test:test --rerun -Dkatachi.snapshot.update=true` を走らせるだけです
（`--rerun` が要るのは、このシステムプロパティは `test` タスクの入力に配線してあるものの、
他の入力が変わっていなければ Gradle が UP-TO-DATE で済ませてしまうからです）。

何のためにあるのかというと、定義の変化を人が差分でレビューするためです。このサンプルは
`":feature:*".module { }` のようなワイルドカードのモジュールキーや sourceSet の糖衣を
いちばん多く使っていて、書き換えたときに読みやすくなっただけなのか、検査する木そのものが
変わってしまったのかは、定義のコードを眺めても分かりません。平坦化した結果をコミット済みの
テキストにしておけば、`git diff` の行数がそのまま答えになります。1行も動かなければ
書き換えは等価、動いたなら何がどう変わったのかがその行に出ています。

これは katachi 自身の検証のために置いてあるもので、katachi を導入するときに書くもの
ではありません。サンプルの定義に手を入れたときに意味が変わっていないことを、katachi の
開発側が確かめるための記録です。

生成物なのに `build/` の外に置いているのは、差分で見せるのが仕事だからです。`.gitignore` が
`build/` を落としている以上、そこに書いた記録は誰のレビューにも出てきません。外に出した
代わりに既定の `files = gitTracked()` の検査対象に入るので、この役割が要ります。役割を
消すと `[UnexpectedDirectory] snapshots` で `:architecture-test:test` が落ちます。

`documented` は書いていません（既定の `true`）。この `snapshots/` が何なのかを説明する
場所は他に無く、一覧から外すとリポジトリを開いた人には由来の分からない `.txt` が
1つ残るだけになるからです。この役割自身のページ（`docs/testing/LayoutSnapshot.md`）も
生成されます。

## Placement

| Module | Path | When to use |
|---|---|---|
|  | `snapshots/layout.txt` |  |

## Examples

- `snapshots/layout.txt` ... 平坦化した layout の全行

## 置いてはいけないもの

- 手書きのメモ。次の `-Dkatachi.snapshot.update=true` で丸ごと上書きされます
- 別の種類の記録。いまの `layout { }` は `snapshots/layout.txt` の1ファイルしか
  認めていないので、足すなら役割を書き換えるところから始まります
