[katachi-sample-kmp](../README.md) / [テスト支援](README.md)

# 生成ドキュメント

この定義から書き出され、リポジトリにコミットされる Markdown

`./gradlew :architecture-test:katachiDocs` が、この定義そのものから
書き出す Markdown です。出力先は `katachi { processors { docs { outputDir } } }` で
`sample/kmp/docs` に向けてあります。

これは手で書かない。`katachiDocs` が書く。ここに書き足した文章は次の生成で消えます。
直す場所は常に定義側で、役割や group の `title` `summary` `description` `example` が
そのままページになります。

生成物なのに `build/` の外に置いているのは、リポジトリを開いた人がそのまま読めるように
するためです。`.gitignore` が `build/` を落としているので、`build/` に出している限り
ページは誰の目にも触れません。外に出した代わりに `files = gitTracked()` の検査対象に
入るので、この役割が要ります。役割を消すと `[UnexpectedDirectory] docs` で
`:architecture-test:test` が落ちます。

古くなっていないかは CI が `--arg mode=check` で見ています。`mode=check` は何も書かずに
ディスク上の内容と突き合わせ、食い違えば例外で落ちるので、定義を変えて生成し忘れたまま
push するとリポジトリルートの `checkSampleKmp` が赤くなります。手元で直すには
`katachiDocs` をもう一度走らせるだけです（`mode` を付けなければ書き込みです）。

`documented = true` にしてあります。生成物であっても一覧に出ないと、この `docs/` が
何なのかがどこにも書かれていないことになるからです。この役割自身のページ
（`docs/testing/GeneratedDocumentation.md`）も生成されます。

置いてはいけないもの:

- 手書きのドキュメント。`docs/` 配下の `*.md` は生成のたびに作り直され、この定義が
  作らないページは削除されます
- `.md` 以外の資源。いまの `layout { }` は `*.md` しか認めていないので、画像を足すなら
  役割を書き換えるところから始まります

`layout { }` の `**` は0段以上に一致するので、索引の `docs/README.md` も
`docs/<group>/README.md` も `docs/<group>/<役割>.md` も1行で覆えます。group を入れ子に
してもこの行は変わりません。索引だけワイルドカード無しで別に書いてあり、そちらは
`required` です。1度も生成していない状態がそこで見つかります。

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
|  | `docs/README.md` |  |
|  | `docs/**/*.md` |  |

## 例

- `docs/README.md` ... 全ページの索引と、group ごとの一覧
- `docs/ui/Component.md` ... 役割1つのページ
- `docs/ui/README.md` ... group 1つのページ
