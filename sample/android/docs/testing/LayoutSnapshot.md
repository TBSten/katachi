[katachi-sample-android](../README.md) / [テスト](README.md)

# レイアウトのスナップショット

この定義を平坦化して全行書き出した記録。定義の変化を人が差分でレビューするためにある

`:architecture-test:test` が、この定義を平坦化した結果を1行1エントリで書き出したもの。
1行は `<役割の qualifiedName>` `<パス>` `<種別>` `<required|optional>` の4つ組で、
`layout { }` が最終的に何を許しているのかがそのまま並ぶ。

これは手で編集しない。`LayoutSnapshotSpec` が書く。更新するには
`./gradlew :architecture-test:test -Dkatachi.snapshot.update=true` を走らせるだけ。

何のためにあるかというと、定義の変化を人が差分でレビューするため。9つのモジュールに
散った役割を `.module { }` や `modulePackage` で書き直したとき、読みやすくなっただけ
なのか、検査する木そのものが変わってしまったのかは、定義のコードを眺めても分からない。
平坦化した結果をコミット済みのテキストにしておけば、`git diff` の行数がそのまま答えに
なる。1行も動かなければ書き換えは等価、動いたなら何がどう変わったのかがその行に出ている。

これは katachi 自身の検証のために置いてあるもので、katachi を導入するときに書くもの
ではない。サンプルの定義に手を入れたときに意味が変わっていないことを、katachi の開発側が
確かめるための記録。

生成物なのに `build/` の外に置いているのは、差分で見せるのが仕事だから。コミットされて
いない記録は誰のレビューにも出てこない。その代わり既定の `files = gitTracked()` の
検査対象に入るので、この役割が要る。役割を消すと `[UnexpectedDirectory] snapshots` で
`:architecture-test:test` が落ちる。

`documented` は書いていない（既定の `true`）。この `snapshots/` が何なのかを説明する
場所は他に無く、一覧から外すとリポジトリを開いた人には由来の分からない `.txt` が
1つ残るだけになる。`GeneratedDocumentation` と同じ扱いで、`tool` グループの
`Documentation` が `documented = false` なのとは逆側。

## Placement

| Module | Path | When to use |
|---|---|---|
|  | `snapshots/layout.txt` |  |

## Examples

- `snapshots/layout.txt` ... 平坦化した layout の全行

## 置いてはいけないもの

- 手書きのメモ。次の `-Dkatachi.snapshot.update=true` で丸ごと上書きされる
- 別の種類の記録。いまの `layout { }` は `snapshots/layout.txt` の1ファイルしか
  認めていないので、足すなら役割を書き換えるところから始まる
