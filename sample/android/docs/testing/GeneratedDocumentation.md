[katachi-sample-android](../README.md) / [テスト](README.md)

# 生成ドキュメント

この定義から書き出され、リポジトリにコミットされる Markdown

`./gradlew :architecture-test:katachiDocs` が、この定義そのものから
書き出す Markdown。出力先は `katachi { processors { docs { outputDir } } }` で
`sample/android/docs` に向けてある。

これは手で書かない。`katachiDocs` が書く。ここに書き足した文章は次の生成で消える。
直す場所は常に定義側で、役割や group の `title` `summary` `description` `example` が
そのままページになる。

`tool` グループの `Documentation` 役割（ルートの `README.md`）とは逆側の役割。
あちらは人が書く散文で、定義からは出てこないこと（環境の用意、AGP を上げられない理由、
検査の回し方）を引き受ける。こちらは人が1文字も書かない。同じ「ドキュメント」でも、
書き換えてよい場所とよくない場所がまるで違うので、1つの役割にまとめていない。

生成物なのに `build/` の外に置いているのは、リポジトリを開いた人がそのまま読めるように
するため。その代わり既定の `files = gitTracked()` の検査対象に入るので、この役割が要る。
役割を消すと `[UnexpectedDirectory] docs` で `:architecture-test:test` が落ちる。

古くなっていないかは CI が `--arg mode=check` で見ている。`mode=check` は何も書かずに
ディスク上の内容と突き合わせ、食い違えば例外で落ちるので、定義を変えて生成し忘れたまま
push するとリポジトリルートの `checkSampleAndroid` が赤くなる。手元で直すには
`katachiDocs` をもう一度走らせるだけ（`mode` を付けなければ書き込み）。

`documented = true`。生成物であっても一覧に出ないと、この `docs/` が何なのかが
どこにも書かれていないことになる。この役割自身のページ
（`docs/testing/GeneratedDocumentation.md`）も生成される。`Documentation` が
`documented = false` なのと対になっていて、同じグループに置かなかった理由でもある。

`layout { }` の `**` は0段以上に一致するので、索引の `docs/README.md` も
`docs/<group>/README.md` も `docs/<group>/<役割>.md` も1行で覆える。group を入れ子に
してもこの行は変わらない。索引だけワイルドカード無しで別に書いてあり、そちらは
`required`。1度も生成していない状態がそこで見つかる。

## Placement

| Module | Path | When to use |
|---|---|---|
|  | `docs/README.md` |  |
|  | `docs/**/*.md` |  |

## Examples

- `docs/README.md` ... 全ページの索引と、group ごとの一覧
- `docs/feature/Screen.md` ... 役割1つのページ
- `docs/feature/README.md` ... group 1つのページ

## 置いてはいけないもの

- 手書きのドキュメント。`docs/` 配下の `*.md` は生成のたびに作り直され、この定義が
  作らないページは削除される
- `.md` 以外の資源。いまの `layout { }` は `*.md` しか認めていないので、画像を足すなら
  役割を書き換えるところから始まる
