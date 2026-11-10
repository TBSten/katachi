# docs

docs・README・手順書の文言・例への判断（手順 2・5・6・11）。形は [README.md](README.md)。

## 直さない: 指示語「これらは」
- decision: 直さない
- file: ja/get-started/comparison-with-other-tools.mdx
- quote: これらは **katachi と排他的とは限りません**
- rule: D3-3（「これら」ではなく指すものを書く）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: 「Konsist や ArchUnit などは、**katachi と排他的とは限りません**。」

## 直さない: katachi-konsist に触れずに FileConstraintCheck() を案内している（要確認）
- decision: 直さない
- file: ja/get-started/motivation.mdx
- quote: assert(FileConstraintCheck())
- rule: A4（別モジュールにあることと `assert(FileConstraintCheck())` を、ページ間で同じ言い方にする）／C1（依存に `katachi-konsist` を足す）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 該当: 「テストで `assert(FileConstraintCheck())` を呼ぶと…」。`ktFileContent { }` が別モジュール `katachi-konsist` に入っていることを書かない（comparison と first-architecture は書いている）
- 直し方の案: 導入前の概説なので「`katachi-konsist` を依存に足すと」と一言添えるか、first-architecture へのリンクにする。導入ページに任せる方針なら直さない
- 同じ指摘: faq.mdx（「テストで `assert(FileConstraintCheck())` を呼べば…」）

## 直さない: 「通さないガードレール」
- decision: 直さない
- file: ja/get-started/motivation.mdx
- quote: ガードレールとして働くツール
- rule: D3-7（比喩。「通さない」も検査の結果の言い方）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: 「ルールを無視したコードを検出するツール」

## 直さない: 読み飛ばしても先に進める補足が caution になっている
- decision: 直さない
- file: ja/get-started/motivation.mdx
- quote: :::caution[Scope の外は検証されない]
- rule: D3-6（読み飛ばされては困る注意だけを Aside にする）。直後の段落の前置きで、読み飛ばしても先に進める補足
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: 地の文にして、FAQ への案内を残すか削る（D3-5）

## 直さない: Role に summary が無い（要確認）
- decision: 直さない
- file: ja/get-started/motivation.mdx
- quote: .role {
- rule: B10（完成形の Role に `summary`）。`// ...` で省いた断片ではなく見えるが、`package`/`import` も無いので完成形ではないとみて p1
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: 断片と分かる `// ...` を足す、または `summary` を1行足す

## 直さない: Role に summary が無い（要確認）
- decision: 直さない
- file: ja/get-started/motivation.mdx
- quote: .role {
- rule: B10（完成形の Role に `summary`）。`// ...` で省いた断片ではなく見えるが、`package`/`import` も無いので完成形ではないとみて p1
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: 断片と分かる `// ...` を足す、または `summary` を1行足す

## 直さない: Role に summary が無い（要確認）
- decision: 直さない
- file: ja/get-started/comparison-with-other-tools.mdx
- quote: .role {
- rule: B10（完成形の Role に `summary`）。`// ...` で省いた断片ではなく見えるが、`package`/`import` も無いので完成形ではないとみて p1
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: 断片と分かる `// ...` を足す、または `summary` を1行足す

## 直さない: 2 ファイル並べの例に @file:OptIn と import が無い（要確認）
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: // roles/AndroidManifest.kt
- rule: コード例は写してそのまま動く形にする（@file:OptIn と import を省かない）
- decided: 2026-11-10 261109-01 KATACHI-322
- by: 利用者（TODO.html、02:20 JST「やらない」）

手順 11 の p5。`roles/AndroidManifest.kt` と `roles/AndroidResource.kt` を 1 つのコードブロックに並べた例（sample/android からの引用）。

## 直さない: コードの直後にサンプルへのリンクが無い（TODO コメント）
- decision: 直さない
- file: ja/recipes/ktlint.mdx
- quote: TODO: C16 コードの直後にサンプルへのリンクが無い
- rule: C16（コードの直後に sample のファイルへのリンクを置く）
- decided: 2026-11-10 261109-01 KATACHI-322
- by: 利用者（322 のスレッド。手順 11 の p4 以下を Review に登録する前の選別）

sample/ に ktlint の例が無いので、TODO コメントのまま残す。detekt・github のページも同じ判断（下の 2 件）。手順 11 で「TODO コメントが残っている」とも出さない。

## 直さない: コードの直後にサンプルへのリンクが無い（TODO コメント）
- decision: 直さない
- file: ja/recipes/detekt.mdx
- quote: TODO: C16 コードの直後にサンプルへのリンクが無い
- rule: C16（コードの直後に sample のファイルへのリンクを置く）
- decided: 2026-11-10 261109-01 KATACHI-322
- by: 利用者（322 のスレッド。手順 11 の p4 以下を Review に登録する前の選別）

ktlint と同じ。

## 直さない: コードの直後にサンプルへのリンクが無い（TODO コメント）
- decision: 直さない
- file: ja/recipes/github.mdx
- quote: TODO: C16 コードの直後にサンプルへのリンクが無い
- rule: C16（コードの直後に sample のファイルへのリンクを置く）
- decided: 2026-11-10 261109-01 KATACHI-322
- by: 利用者（322 のスレッド。手順 11 の p4 以下を Review に登録する前の選別）

ktlint と同じ。

## 方針: AI 下書きの印が無い（D1-2・E8）
- decision: 方針
- file: docs/src/content/docs/**
- match: AI\s*下書きの印が無い
- rule: D1-2（AI が書いたページには下書きの印）／E8（AGENTS の木とページの印が合わない）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（チャット）

利用者の一言: 「この辺りは一律直さないでいいや」

範囲: docs のページに AI 下書きの印（本文先頭の `<Aside type="note" title="AI が書いた下書き">` や title の `[AI生成]`）が無い、
AGENTS の木では [AI生成] なのにページに印が無い、という指摘は出さない。

範囲の外: 印の形が決まりと違う指摘（地の文の末尾に「（AI の下書き）」と書いてある、など）は、この方針に入れずにいつもどおり見る。

元の指摘（261109-01 の手順 11、4 件）: guides/diff-report・cli-and-script・cli-and-script/cli・cli-and-script/katachi-script に印が無い。

## 方針: 「このページの目的」のコメント（D1-1）
- decision: 方針
- file: docs/src/content/docs/**
- match: 目的の?コメント
- rule: D1-1（frontmatter の直後、import より前に「このページの目的」のコメントを 1〜3 行）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（チャット）

利用者の一言: 「この辺りは一律直さないでいいや」

範囲: 「このページの目的」のコメントが無い、import より後にある、という指摘は出さない。

範囲の外: description と本文が合っていない指摘は、目的のコメントに触れていても別の指摘としていつもどおり見る（見出しが match に合わないので外れない）。

元の指摘（261109-01 の手順 11、9 件）: 無い 5 件（guides/kt-file-content・diff-report・cli-and-script・cli-and-script/cli、recipes/github）、import の後 4 件（get-started/comparison-with-other-tools、guides/baseline・kt-file-content/kotlin-mapping・cli-and-script/katachi-script）。

## 方針: 次のステップのカードが processor を勧めている（D1-7・要確認）
- decision: 方針
- file: docs/src/content/docs/**
- match: 次のステップ.*processor
- rule: D1-7（`processor` を前面に出さない。ほとんどの利用者は `assert()` の 1 行しか触らない）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（チャット）

利用者の一言: 「これ系、一律直さないで良い」

範囲: ページの終わりの「次のステップ」のカード（とその description）が processor を勧めている・前面に出している、という指摘は出さない。

範囲の外: 本文や FAQ の中で processor を前面に出している指摘は、同じ D1-7 でもカードではないので、この方針に入れずに 1 件ずつ見る（faq の「自作 Processor」の枝は利用者が popup で「直さない」にしたので、下に 1 件として書いてある）。

元の指摘（261109-01 の手順 11、3 件）: guides/generate-code-from-template・document-generation・diff-report の「次のステップ」のカード。

## 直さない: processor を FAQ の中で前面に出している（要確認）
- decision: 直さない
- file: ja/get-started/faq.mdx
- quote: 自作 Processor
- rule: D1-7（`processor` を前面に出さない。ほとんどの利用者は `assert()` の1行しか触らない）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 該当: mermaid の `D -.-> C["自作 Processor<br/>集計・抽出・別形式への出力"]` と、その後の段落「テストとドキュメントは katachi が用意しているものですが、**この2つに限りません。** … ArchitectureProcessor とそのカスタマイズ を参照してみてください。」
- 直し方の案: 図の点線の枝と、最後の段落（processor への案内）を外す。残すなら「やりたい人だけ」の位置づけで `<details>` にする

## 方針: Role に summary が無い（B10・要確認）
- decision: 方針
- file: **
- match: summary.{0,12}無い
- rule: B10（Role には title と summary を書く）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（チャット）
- revisit: 利用者が summary の扱いを決め直したとき（「一旦」の判断なので、求めると言われたらこの方針を消す）

利用者の一言: 「summary がない系も 一旦直さないでいいや」

範囲: docs・README の例の Role に summary が無い、という指摘は出さない。断片の例・「まとめ」の完成形・README の例のどれでも同じ。
`summary・layout の無い Role` のように layout の無さと一緒に挙がった指摘も、summary の部分はこの方針で外す
（layout が無いことだけが問題の指摘は、見出しに summary が出ないので外れない）。

元の指摘（261109-01 の手順 11、10 件）: get-started/motivation・comparison-with-other-tools の Role（上の「直さない」3 件）、
guides/layout の「まとめ」の UseCase Role、完成形の Role 3 件（kt-file-content 2・generate-code-from-template）、
processor の summary・layout の無い Role、diff-report の Role、README.ja.md の Role。

## 直さない: katachi-konsist に触れずに FileConstraintCheck() を案内している（要確認）
- decision: 直さない
- file: ja/get-started/faq.mdx
- quote: assert(FileConstraintCheck())
- rule: A4／C1
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 該当: 「テストで `assert(FileConstraintCheck())` を呼べば…」。`ktFileContent { }` が別モジュール `katachi-konsist` に入っていることを書かない
- 直し方の案: 「`katachi-konsist` を依存に足すと」と一言添えるか、first-architecture へのリンクにする
- 同じ指摘: motivation.mdx

## 直さない: 2 行 × 1 観点の表
- decision: 直さない
- file: ja/get-started/faq.mdx
- quote: 検証する範囲
- rule: D3-11（複数のものを複数の観点で比べるときだけ表にする）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: 文で書く（直後のコードと FileTree が同じことを示している）

## 直さない: 本文の途中に LinkCard 1 枚
- decision: 直さない
- file: ja/get-started/faq.mdx
- quote: <CardGrid>
- rule: D3-5（別のページへ誘う）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 該当: 本文の途中に `<CardGrid>` で `LinkCard` 1 枚（他ツールとの比較）
- 直し方の案: 地の文のリンクにする

## 直さない: 見出しの ktFileContent がコード表記でない
- decision: 直さない
- file: ja/get-started/comparison-with-other-tools.mdx
- quote: Role の性質を ktFileContent で書く
- rule: A2（API はコード表記）
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: 全部が全部コード表記にすれば良いというものではない。太字と同じくらい目立たせたいかつコードの時にのみ インラインコードにした方がいいという考え

元の指摘:

- 直し方の案: 見出しにバッククォート（`` `ktFileContent { }` ``）

## 直さない: 完成形と書く例に import が無い（要確認）
- decision: 直さない
- file: ja/guides/baseline.mdx
- quote: ここまでの設定を1つにした完成形です。
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 「完成形」と書くが package/import から始まらない断片なので D2-1 の対象外。ただしファイル全体と読まれる恐れがある（role.mdx は title にファイル名を付けている）

## 直さない: 指示語（D3-3）
- decision: 直さない
- file: ja/guides/layout.mdx
- quote: この例では
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: ここは指示語でいいかな

元の指摘:

- 直し方の案: 指すものを書く。「`generated/` の宣言では」

## 直さない: 指示語（D3-3）
- decision: 直さない
- file: ja/guides/layout.mdx
- quote: この例の
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: 指すものを書く。「上の `IdeCodeStyle` では」

## 直さない: ignore() / anyFile() がコード表記でない（A2）
- decision: 直さない
- file: ja/guides/layout.mdx
- quote: ignore, anyFile を乱用
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: `ignore()`・`anyFile()` のコード表記に

## 直さない: ハイライトが閉じかっこまで含んでいる（D2-4）
- decision: 直さない
- file: ja/guides/kt-file-content.mdx
- quote: {6,8-13,15-19}
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- ブロックの 19 行目は `required(...) {` を閉じる `}`
- 直し方の案: `15-19` を `15-18` にする。`required { }` ブロック全体を指す意図なら範囲のままでよい（要確認）

## 直さない: 修飾子の説明で public の扱いが書かれていない（要確認）
- decision: 直さない
- file: ja/guides/kt-file-content/kotlin-mapping.mdx
- quote: public` と `final` を除く
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- `final` は次の文で説明があるが、`public` が除かれる理由が無い
- 直し方の案: 「`public` は書かなくても付いていてよい」を一文で足す

## 直さない: 指示語と半角括弧（D3-3）・「Deny by default」の表記（E12）
- decision: 直さない
- file: ja/guides/kt-file-content.mdx
- quote: (これは katachi が Deny by default の原則に従うためです。)
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: 発生する現象の後にその現象が起こる理由を説明するときの「これは」は良さそう

元の指摘:

- 「これは」が前の文を指す
- 案: 「全ての宣言が禁止されるのは、katachi が Deny by default の原則に従うためです。」と 1 文にする
- 「Deny by default」の大文字始まりは E12 の判断待ち（kotlin-mapping.mdx L22 も同じ）

## 直さない: 指示語（D3-3）
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: これらを必要に応じて参照しながら
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: `context.architecture` や `context.args` など指すものを書く

## 直さない: 手順の途中で別のページ・リファレンスへ誘う（D3-5・要確認）
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: ArchitectureProcessContext
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- `ArchitectureProcessContext` の API リファレンスへ誘っている。特に `decodeFromStringMap` の細部は利用者が最初に知らなくてよい
- 直し方の案: 「次のステップ」の API リファレンスのカードに寄せるか、削る

## 直さない: 手順の途中で別のページ・リファレンスへ誘う（D3-5・要確認）
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: decodeFromStringMap
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- `decodeFromStringMap` の「カンマで分割される型などの細かい決まり」へ誘っている。特に `decodeFromStringMap` の細部は利用者が最初に知らなくてよい
- 直し方の案: 「次のステップ」の API リファレンスのカードに寄せるか、削る

## 直さない: API 名がコード表記でない（A2）
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: layout のファイルの宣言
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: `layout { }`

## 直さない: API 名がコード表記でない（A2）
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: summary, description などのプロパティ
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: `summary`・`description`

## 直さない: API 名がコード表記でない（A2）
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: layout を flat にした
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: `layout { }`

## 直さない: API 名がコード表記でない（A2）
- decision: 直さない
- file: ja/guides/document-generation.mdx
- quote: title / summary / description / example / layout
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: `example()`・`layout { }` の形

## 直さない: IDE プラグインに触れている（D1-6・要確認）
- decision: 直さない
- file: ja/guides/cli-and-script/katachi-script.mdx
- quote: IDE のサポート
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 表「IDE のサポート | katachi IDE plugin が必要 | 標準の IntelliJ IDEA, Android Studio のみ」
- 根拠: D1-6「IDE プラグインは、当面 docs で案内しない」。要件の一言なので p3
- 直し方: 「IDE のサポート」の行ごと削るか、IDE プラグインの語を外す

## 直さない: Kotlin の例が 4 スペースの字下げ（D2-3）
- decision: 直さない
- file: ja/guides/cli-and-script/katachi-script.mdx
- quote: executeCommands(
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 2 スペースに（`<Tabs>` の中はフェンスの字下げの分だけ入れ、コード自体を余計に字下げしない）

## 直さない: Kotlin の例が 4 スペースの字下げ（D2-3）
- decision: 直さない
- file: ja/guides/cli-and-script/katachi-script.mdx
- quote: executeCommands(
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 2 スペースに（`<Tabs>` の中はフェンスの字下げの分だけ入れ、コード自体を余計に字下げしない）

## 直さない: Kotlin の例が 4 スペースの字下げ（D2-3）
- decision: 直さない
- file: ja/guides/cli-and-script/katachi-script.mdx
- quote: executeCommands(
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 2 スペースに（`<Tabs>` の中はフェンスの字下げの分だけ入れ、コード自体を余計に字下げしない）

## 直さない: Kotlin の例が 4 スペースの字下げ（D2-3）
- decision: 直さない
- file: ja/guides/cli-and-script/katachi-script.mdx
- quote: Katachi(
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 2 スペースに（`<Tabs>` の中はフェンスの字下げの分だけ入れ、コード自体を余計に字下げしない）

## 直さない: 指示語（D3-3）
- decision: 直さない
- file: ja/guides/cli-and-script.mdx
- quote: これらを活用する
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 直し方の案: 「CLI と kotlin-script 用パッケージを活用する」

## 直さない: Kotlin の例が 4 スペースの字下げ（D2-3）
- decision: 直さない
- file: README.ja.md
- quote: .template
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: デフォルトは Kotlin ソースは インデント 4スペースだけど、README[.*].md だけ例外的に 2でOKとしたい

元の指摘:

- L15〜21・L52〜72・L87〜94。2 スペースに直す。`.template` の継続行は +2

## 直さない: 例が package・import を書かず、省略とも書いていない（D2-2）
- decision: 直さない
- file: README.ja.md
- quote: // architecture-test/src/test/kotlin/
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 先頭にファイルパスがあり、ファイル全体に読める
- 直し方の案: ブロックの先頭に `// package / import の行は省略` を足す

## 直さない: 「Gradle 8.0+」と index.md の「Gradle 7.6.3 以上」（要確認）
- decision: 直さない
- file: README.ja.md
- quote: Kotlin 2.2+・Gradle
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: gradle のチェックはしないでいいかな... 現状の最低バージョン調べるのめんどいし...

元の指摘:

- 2つのページで対応バージョンの言い方が違う。`doctor` の判定に合わせて正しい値を確かめてから揃える

## 直さない: A2（API のコード表記）と D1-4（LinkCard にコードを書かない）の優先（E16・p1）
- decision: 直さない
- file: docs/AGENTS.md
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- motivation.mdx の LinkCard の description は `architecture { }`・`.template { }` をバッククォート無しで書いている。D1-4 は守っているが A2 の「コード表記」にはなっていない。どちらを優先するかの記述が無い
