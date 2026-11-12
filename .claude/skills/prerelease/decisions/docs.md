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

## 方針: CLI のページのコマンド一覧に `katachi lint` が無い
- decision: 方針
- file: docs/src/content/docs/**
- match: katachi lint.{0,40}(無い|出てこない|載っていない)|CLI.{0,20}(ページ|一覧).{0,40}lint
- decided: 2026-11-12 261112-01 KATACHI-368
- by: 利用者（スレッド）

利用者の一言: 「katachi lint が CLI のページに出てこない の feedback 一律対応しないとする。」

範囲: docs の CLI のページ（guides/cli-and-script/cli など）に `katachi lint` が載っていない・コマンド一覧に無い、という指摘は出さない。Review・Feedback で同じ指摘が来たら「直さない」として扱う。

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

## 直さない: 完成形と書く例に import が無い（要確認）
- decision: 直さない
- file: ja/guides/role.mdx
- quote: kt title=domain/UseCaseRole.kt nocollapse
- decided: 2026-11-10 261109-01 KATACHI-333
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

- 「完成形」と書くが package/import から始まらない断片なので D2-1 の対象外。ただしファイル全体と読まれる恐れがある（role.mdx は title にファイル名を付けている）

## このまま: E1 role.mdx の例が feature ごとに layout を書く（「変更を必要としない Role」と食い違う）
- decision: このまま
- file: ja/guides/role.mdx
- quote: "feature-home/src/main/kotlin/useCase" / "*UseCase".ktFile()
- rule: ガイド同士の食い違い E1（B5「良い Role 定義は変更を必要としない」）
- decided: 2026-11-11 261110-01 KATACHI-354
- by: 利用者（チャット「4, 7, 9 以外はよしなに直しといて」。おすすめは E4 だけ直してほかはこのまま）

置き場所を宣言する例として、モジュールを 1 つずつ書く形のまま残す。理由の一言なし。

## このまま: E3 テンプレートのガイドに id を書くか
- decision: このまま
- file: ja/guides/generate-code-from-template.mdx
- quote: .template(id = "preview") { }
- rule: ガイド同士の食い違い E3（AGENTS D3-5「テンプレートのガイドから id を削った」）
- decided: 2026-11-11 261110-01 KATACHI-354
- by: 利用者（チャット。E4 だけ直してほかはこのまま）

1 つの Role に 2 つ以上のテンプレートを付けるときの `id` の説明は残す。理由の一言なし。

## このまま: E18 kotlin-mapping が not. を見本にしている（C5 と食い違う）
- decision: このまま
- file: ja/guides/kt-file-content/kotlin-mapping.mdx
- quote: not.fileAnnotation("JvmName")
- rule: ガイド同士の食い違い E18（C5「禁止したいものを 1 つずつ書く形を見本にしない」）
- decided: 2026-11-11 261110-01 KATACHI-354
- by: 利用者（チャット。E4 だけ直してほかはこのまま）

対応表の `not.` の行は残す。理由の一言なし。

## このまま: E13 AGENTS の決まりが skill（英語）に及ぶか
- decision: このまま
- file: skills/katachi/**
- rule: ガイド同士の食い違い E13（AGENTS の 2 スペース・group 名の大文字・@file:OptIn・B20 の命名）
- decided: 2026-11-11 261110-01 KATACHI-354
- by: 利用者（チャット。E4 だけ直してほかはこのまま）

AGENTS（docs サイトの決まり）を skill に広げない。skill の小文字 group・4 スペースの例・`feature.ui` などを、AGENTS の決まりを根拠に指摘しない。

2026-11-12 261112-01 KATACHI-368 で見直した（#81 で skill 自身が「Role と group は UpperCamelCase」を決まりにし、skill の中の小文字 group は無くなった）。利用者「あくまで katachi 本体とサンプル、他プロジェクトでは推奨という立ち位置のルールなので skill とかでは強制はしなくていい」。判断はこのまま。skill（とそれを使う利用者のプロジェクト）では推奨の扱いで、指摘しない。katachi 本体とサンプルは守る側。

## このまま: ガイド同士の食い違い（E5・E7・E9・E10・E15・E17）
- decision: このまま
- file: ja/**
- rule: ガイド同士の食い違い E5（Role 名の大文字始まり）・E7（名前付きの ktFileContent）・E9（テンプレートの無い Role の capture）・E10（「Baseline ファイル」と「baseline のファイル」）・E15（Role のガイドの Aside と ### の節）・E17（D2-1 と D2-2 の断片の注記）
- decided: 2026-11-11 261110-01 KATACHI-354
- by: 利用者（チャット。E4 だけ直してほかはこのまま）

priority 1〜3 のルールの側の食い違い。どれもルールも例も今のまま。同じ E の番号・同じ趣旨の食い違いは次回も出さない。

## 方針: コード表記でない
- decision: 方針
- file: **
- match: コード表記(で|になってい|にしてい)ない
- rule: 「API 名・ファイル名はコード表記にする」系のチェック項目（以前の A2）
- decided: 2026-11-11 KATACHI-359
- by: 利用者（チャット）

利用者の一言: 「ドキュメント、コード表記にするのはあくまで文章中の強調したい部分だけという運用にしたい」

インラインコードは、文章中で太字と同じくらい目立たせたいコードにだけ使う（`docs/AGENTS.md` の「文章の方針」）。名前を出すだけの箇所がコード表記になっていないことは指摘しない。上の「直さない: 見出しの ktFileContent がコード表記でない」（2026-11-10）と同じ考え。

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".claude/skills/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: agent skill は references とかも入るしまあこれでいいかなと

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".claude/hooks/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".cursor/skills/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".devin/skills/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".windsurf/skills/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".github/skills/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".cline/skills/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".cline/hooks/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".junie/skills/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".agents/skills/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L660

## 直さない: ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: ".agents/skills/**/*".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」
**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る
同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589

## 直さない: Tabs の 8 つのラベルが 1 行に収まらず、1280px でも末尾が切れる
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: <Tabs>
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: いやこのままでええかな

元の指摘:

**ルールに反する点**: 手順 6 の巡回から。Role ごとの 8 つの `<TabItem label="…">`（UiState・ViewModel・Screen・UseCase・DomainModel・RepositoryInterface・RepositoryImplementation・DataSource）が 1 行に収まらず、幅 1280px でも末尾のラベルが切れる。
**直し方の案**: ラベルを短くする（`Repository`・`RepositoryImpl` など）、タブを Role の group（ui・domain・data）ごとの 3 つにまとめる、またはタブをやめて見出しで並べる。

## 直さない: 「いずれも Gradle script で設定が可能」と表が食い違う
- decision: 直さない
- file: ja/guides/diff-report.mdx
- quote: いずれも コマンド 実行時, Gradle script で設定が可能です。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: まあ一旦いいや

元の指摘:

**ルールに反する点**: D3-2（言い切れないことを言い切っている）。根拠: L210 の `fullImportanceLines` は「コマンドの --arg でのみ指定できます」で、「いずれも」に当てはまらない。写した利用者が Gradle script に `fullImportanceLines` を書こうとして迷う
**直し方の案**: 「ほとんどのオプションは、コマンド実行時と Gradle script のどちらでも設定できます（`fullImportanceLines` はコマンドだけ）。」。ついでに全角の語間のスペースと半角の「,」も直す（「コマンド 実行時, Gradle script で」→「コマンド実行時と Gradle script で」）

## 直さない: processor ページが processor を「重要な構成要素」と前面に出している
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: katachi の重要な構成要素の 1 つである ArchitectureProcessor を解説し、katachi の理解を深めます。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: katachi の内部のアーキテクチャ上は重要な要素ではあるのでこのままかな

元の指摘:

**ルールに反する点**: D1-7（`processor` を前面に出さない。ほとんどの利用者は `assert()` の 1 行しか触らない。ページが自分で短い前提を 2〜3 行持つ）。L42 の「katachi は大きく分けて…2 つの要素でできています」も同じ方向
**直し方の案**: 冒頭に「ほとんどの利用者は `projectArchitecture.assert()` の 1 行しか書かないので、このページは読まなくても katachi を使えます」の趣旨を 2〜3 行で足し、「重要な構成要素」「理解を深めます」を外す

## 直さない: android-three-layer: `DomainModel` が `"*".ktFile()` だけ（受け皿の Role）
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: "app" / mainSourceSet / kotlin / appPackageDir / "domain/model" / "*".ktFile()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（ファイル名のパターンも `ktFileContent { }` も無い、ここにあれば何でもの Role）。同じページの UiState・ViewModel・UseCase などは名前のパターンと `ktFileContent` でファイルの中身まで決めているので、DomainModel だけ形が違う。D2-5（ルールを守った見本）にも数える
**直し方の案**: `ktFileContent { `package`(matchingDirectory); `import`("**"); required(`class`, fileName) { allow(anythingElse) } }` のように、1 ファイルに 1 つの class だけを置く決まりを足す。足さない意図なら summary かコメントに「名前も中身も決めない」と書く

## 直さない: gradle: `ConventionPluginSource` が `"**" / "*".ktFile()`（受け皿の Role）
- decision: 直さない
- file: ja/recipes/gradle.mdx
- quote: "**" / "*".ktFile() // パッケージを切ったクラスも許す
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B13（`"**/*.kt"` のように「ここにあれば何でも」の layout。ファイル名のパターンか `ktFileContent { }` を付ける）。根拠: skill 3-2「Do not stop at placement」。convention plugin のヘルパーのクラスは名前で絞れないので、ktFileContent を付けない理由が分からない
**直し方の案**: `ktFileContent { }` で最低限（`package`・`import`・`allow(anythingElse)` など）を書くか、コメントで「中身は問わない」と明記する

## 直さない: skill が出力の写しを英語だけで書いている: `kotlin is not on PATH`（D1-14）
- decision: 直さない
- file: skills/katachi/references/setup/step0/0-1-permissions.md
- quote: kotlin is not on PATH
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D1-14（skill に `katachi` の出力を書くときは ja / en の 2 通りを並べる）。出力は `katachi-script/.../internal/Messages.kt:96-97` に ja（「kotlin が PATH にありません。brew install kotlin で入れてください。」）と en がある。0-2 は「checklist の言語で答える」と言うが、この時点では checklist が無く、`--lang` を付けないと環境の言語になる。日本語の出力が出ると、この行と合わせて読めない
**直し方の案**: 「`kotlin is not on PATH`（ja: `kotlin が PATH にありません`）」と並べる

## 直さない: skill が出力の写しを英語だけで書いている: Kotlin が古いときの doctor の警告（D1-14）
- decision: 直さない
- file: skills/katachi/references/setup/step0/0-2-working-directory.md
- quote: This project uses Kotlin 1.9.10
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D1-14。同じ行の `"Gradle needs upgrading too"` も英語だけ。`Messages.kt:262-265`・`:283-290` に ja（「このプロジェクトの Kotlin は … です。katachi は Kotlin … 以降が要ります」「Gradle も上げる必要があります」）がある。0-2 の L5 は「checklist の言語（ja）で `katachi` が答える」と書くので、日本語のユーザではここが日本語の出力になる
**直し方の案**: 両方に ja の文言を並べる（「このプロジェクトの Kotlin は 1.9.10 です。katachi は Kotlin 2.2 以降が要ります」「Gradle も上げる必要があります」）

## 直さない: 「うまく動かないとき」が出力を 1 行ずつ解説している（D1-8・要確認）
- decision: 直さない
- file: README.ja.md
- quote: Failed to load JUnit Platform
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D1-8（エラーメッセージの読み方をドキュメント化しない）。L112〜118 の 4 項目は「出力 → 原因」の対応表で、`Failed to load JUnit Platform`・`Cannot create Launcher without at least one TestEngine`・`did not discover any tests to execute` は JUnit の出力、`[UncheckedFileConstraint]` だけが katachi の出力。`<details>` に畳んであり（D3-6 の形）、first-architecture.mdx L212 にも `[UncheckedFileConstraint]` は書いてある。katachi のエラーの読み方ではなく、導入で詰まる 4 点の案内とも読める
**直し方の案**: 直さないでよい。外すなら `<details>` ごと first-architecture に任せる

## 直さない: 「全部使うと」の完成形に import が無く、省いたとも書いていない
- decision: 直さない
- file: ja/guides/generate-code-from-template.mdx
- quote: このページで扱った書き方を全部使うと、テンプレートは次のようになります。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D2-2（ファイル全体に読める例は import をそろえる。断片で省くなら省いたと書く）／C15（`.template`・`capture()` を使う例）。根拠: 「全部使うと」で締める例で、`"FeatureReadme".role {` から始まる。`import me.tbsten.katachi.dsl.template` などが無い
**直し方の案**: コードの先頭に `// package / import の行は省略` を足す

## 直さない: 同じことを言い換えた箇条書き
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: アーキテクチャを定義しただけでは何も起こりません。その定義を使って何かを行うのが ArchitectureProcessor です。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-5（同じことの言い換え）。L61「アーキテクチャ定義そのものは何も実行しません。…受け取って何かをするのが ArchitectureProcessor」と同じ内容
**直し方の案**: 2 つの箇条書きを削るか、L61 の 1 文にまとめる。L112「いずれもプロジェクトのアーキテクチャ定義を受け取り、検証や生成に使っています。」も表の説明の繰り返しなので一緒に削れる

## 直さない: 読み飛ばしても先に進める注意が caution
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: 用意した実行方法は、processor の KDoc などにドキュメントとして書き、実行方法まで利用者に伝えてください。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: 全体的に caution は読み飛ばさないだろうので、D3-6の例外になる気がしてる

元の指摘:

**ルールに反する点**: D3-6（読み飛ばされては困る注意だけを Aside に）。読まなくても手順は完了できる推奨事項で、C11 の「実行方法を KDoc に書く」を caution にしている
**直し方の案**: 「2. 実行方法を用意する」の本文に一文として移す。caution のままにするなら、書かないとどうなるか（利用者が `process` を直接呼ぶことになる）を添える

## 直さない: 「完成です」と書く例が断片（package・import・opt-in が無い）
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: 独自の processor は、`ArchitectureProcessor` を実装し、`Architecture` の拡張関数として実行方法を用意すると完成です。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: ノイズなのでいらん

元の指摘:

**ルールに反する点**: D2-2（ファイル全体に読める例は import をそろえる。断片なら省いたと書く）、D2-1（ファイル全体なら `@file:OptIn`）。`ArchitectureProcessor` は `@ExperimentalKatachiApi` なので、写すと opt-in の指摘が出る
**直し方の案**: ブロック先頭に `// package / import の行は省略` を足す。ファイル全体の例にするなら `@file:OptIn(ExperimentalKatachiApi::class)` と import を書く

## 直さない: ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: "Hooks".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる
**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい
同じ指摘: ja/recipes/ai-agent.mdx L268, ja/recipes/ai-agent.mdx L372, ja/recipes/ai-agent.mdx L452, ja/recipes/ai-agent.mdx L601, ja/recipes/ai-agent.mdx L680

## 直さない: ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: "Hooks".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる
**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい
同じ指摘: ja/recipes/ai-agent.mdx L87, ja/recipes/ai-agent.mdx L372, ja/recipes/ai-agent.mdx L452, ja/recipes/ai-agent.mdx L601, ja/recipes/ai-agent.mdx L680

## 直さない: ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: "Hooks".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる
**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい
同じ指摘: ja/recipes/ai-agent.mdx L87, ja/recipes/ai-agent.mdx L268, ja/recipes/ai-agent.mdx L452, ja/recipes/ai-agent.mdx L601, ja/recipes/ai-agent.mdx L680

## 直さない: ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: "Hooks".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる
**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい
同じ指摘: ja/recipes/ai-agent.mdx L87, ja/recipes/ai-agent.mdx L268, ja/recipes/ai-agent.mdx L372, ja/recipes/ai-agent.mdx L601, ja/recipes/ai-agent.mdx L680

## 直さない: ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: "Hooks".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる
**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい
同じ指摘: ja/recipes/ai-agent.mdx L87, ja/recipes/ai-agent.mdx L268, ja/recipes/ai-agent.mdx L372, ja/recipes/ai-agent.mdx L452, ja/recipes/ai-agent.mdx L680

## 直さない: ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: "Hooks".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる
**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい
同じ指摘: ja/recipes/ai-agent.mdx L87, ja/recipes/ai-agent.mdx L268, ja/recipes/ai-agent.mdx L372, ja/recipes/ai-agent.mdx L452, ja/recipes/ai-agent.mdx L601

## 直さない: android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」
**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい
同じ指摘: ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## 直さない: android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」
**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい
同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## 直さない: android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」
**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい
同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## 直さない: android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」
**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい
同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## 直さない: android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」
**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい
同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## 直さない: android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」
**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい
同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## 直さない: android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」
**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい
同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L338

## 直さない: android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」
**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい
同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307

## 直さない: 6-A〜6-D のファイルの 1 行目が `##`（D1-15）
- decision: 直さない
- file: skills/katachi/references/setup/README.md
- quote: | 6-A |
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D1-15（各ファイルの 1 行目は `## N.` / `### N-M.`）。README の表の題は 27 ファイルすべてと一致したが、`step6/6-A`〜`6-D`（表の L35〜38）の 1 行目だけが `## 6-A.`〜`## 6-D.` で、同じ N-M の形の 6-0 は `### 6-0.`。6-A〜6-D は「報告のあとの選択」なので別格として `##` にしたとも読める
**直し方の案**: `### 6-A.` などにそろえるか、README に「6-A〜6-D は `##`」と書く（`step6/` は skill-2 の担当なので、そちらでも出うる）

## 直さない: skill が出力の写しを英語だけで書いている: scaffold の `already exists`（D1-14）
- decision: 直さない
- file: skills/katachi/references/setup/step0/0-1-permissions.md
- quote: already exists
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D1-14。`Messages.kt:430-433` は ja「がすでに存在します」・en「already exists」。ただしここは「probe でディレクトリを作るな」の理由の説明で、出力と照合する指示ではないので p2
**直し方の案**: 「`already exists`（ja: `がすでに存在します`）」と並べるか、「すでにあると言って止まる」と出力の文言を引用しない形にする

## 直さない: 削除済みの `katachi agent init` が旧作業ディレクトリの説明に残っている（B27）
- decision: 直さない
- file: skills/katachi/references/setup/step0/README.md
- quote: katachi agent init
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B27（削除したコマンドを書かない。`katachi agent init` は PR #65 で削除済み）。ただしここは「旧版が作った作業ディレクトリがあったら」の説明で、手順としては案内していない。手順 5 の katachi-script.mdx・cli.mdx とは別の箇所（手順 5 の 3 件には入っていない）
**直し方の案**: 「older katachi が作った作業ディレクトリ（`check-list.html` に今の手順の項目が無いもの）」と、削除済みのコマンド名を出さずに書く

## 直さない: 4-2 の baseline の例だけ `@OptIn` を `val` に付ける形で、3-2 の `@file:OptIn` と違う
- decision: 直さない
- file: skills/katachi/references/setup/step4/4-2-handle-errors.md
- quote: put `@OptIn(ExperimentalKatachiApi::class)` on `val projectArchitecture
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D2-1 / C15 の趣旨（ファイル全体は `@file:OptIn(ExperimentalKatachiApi::class)` と `import me.tbsten.katachi.ExperimentalKatachiApi` をそろえる。3-2 L20〜24 はその形）。`baseline()` は `@ExperimentalKatachiApi`（`ArchitectureScope.kt` L73）なので両方動くが、skill の中で形が 2 つになる。`import` を足すことも書いていない
**直し方の案**: 「`ProjectArchitecture.kt` は 3-2 の `@file:OptIn` で始まっているはずなので、そのまま `baseline()` を書く」と書く。無ければ「3-2 の experimental APIs の 2 行を足す」と案内する

## 直さない: 「これは」が前の文を指している
- decision: 直さない
- file: ja/get-started/faq.mdx
- quote: これは **fail-safe defaults**（デフォルトの振る舞いを安全なほう、つまり許可しないほうにしておく）と呼ばれる設計原則の1つです。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-3。「想定外のものをまず疑う考え方」を指す（理由の説明ではなく名前の紹介なので、decisions の「これは」の許容とは別）
**直し方の案**: 「想定外のものをまず疑う考え方は **fail-safe defaults** …と呼ばれる設計原則の1つです。」と 1 文にする

## 直さない: 「これは」が前の段落を指している
- decision: 直さない
- file: ja/get-started/faq.mdx
- quote: これはそのまま、**チームがアーキテクチャについて合意し直す作業**でもあります。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-3
**直し方の案**: 「違反を1つずつ片付けることは、そのまま…」

## 直さない: ProjectArchitecture.kt に group と Role の定義を直接書いている
- decision: 直さない
- file: ja/get-started/first-architecture.mdx
- quote: "Domain".group {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: まあここは話したいことと違うことが膨らんじゃって嫌なので直さんでええかなと

元の指摘:

**ルールに反する点**: B20（`ProjectArchitecture.kt` は group の関数を呼ぶだけ）。ただし B20 は skill の決まりで、ガイドは書いていない（E13 と同じく、skill の決まりが docs に及ぶか不明）。最初の 1 本の例としては、1 ファイルのほうが写しやすい
**直し方の案**: 直さない。ガイドに決まりを書くなら、この例は `"Domain".group` の中身を別ファイルにする必要がある

## 直さない: 「これが」が前の文を指している
- decision: 直さない
- file: ja/get-started/first-architecture.mdx
- quote: katachi は許可リスト方式なので、これが正常な出発点です。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-3。「宣言していないファイルやディレクトリが違反として出る」ことを指す
**直し方の案**: 「katachi は許可リスト方式なので、宣言していないものが違反として出るのが正常な出発点です。」

## 直さない: 「ガードレールとして機能する」は比喩
- decision: 直さない
- file: ja/get-started/first-architecture.mdx
- quote: katachi がガードレールとして機能するか検証させましょう。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: この比喩は許容かな。許容していい比喩と許容しちゃいけない比喩があって、それを考慮した指摘をして欲しい気はする（何を許容していいのか・しちゃいかんのかの区別はまだついてない）

元の指摘:

**ルールに反する点**: D3-7（挙動を比喩で言わず、何が起きるかを書く）。motivation L34 の「通さないガードレール」は decisions で「直さない」だが、これは別のファイルで、「通さない」を含まない。同じ趣旨なので判断に寄せる
**直し方の案**: 「katachi が違反を検出するか検証させましょう。」

## 直さない: 「これを」が指すものを書いていない
- decision: 直さない
- file: ja/get-started/motivation.mdx
- quote: これを宣言的な DSL で書くと、プロジェクト全体のディレクトリ構造をテストできるようになります。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-3（指示語ではなく指すものを書く）。直前の箇条書きの 2 項目（Role と配置）を指す
**直し方の案**: 「Role と配置を宣言的な DSL で書くと、…」

## 直さない: 「このルール」が指すものが曖昧
- decision: 直さない
- file: ja/get-started/motivation.mdx
- quote: このルールによって検知される例
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-3。「ルール」は直前では `projectArchitecture` の宣言だが、その節では「ルール」と呼んでいない
**直し方の案**: 「この定義で検知される例」

## 直さない: group を architecture の中に直接書いている（baseline の設定）
- decision: 直さない
- file: ja/guides/baseline.mdx
- quote: "Tool".group {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: ここは baseline のはなしをしたくて、関数分けると逆にノイズになりそうなのでこのままかな

元の指摘:

**ルールに反する点**: B20（同上）。この例は `// ...` で省いた断片で、`baselineFile()` の関数は同じブロックの下に書いてある
**直し方の案**: 直さない（断片の例）

## 直さない: 非生物主語で「明確になります」（group の説明）
- decision: 直さない
- file: ja/guides/basic-api.mdx
- quote: Role のグループをドメインレイヤーとして DSL で表すと
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: これは今のままが自然に思う

元の指摘:

**ルールに反する点**: D3-15（「〜すると、〜が明確になります」と非生物主語で書き、同じ文で「分かりやすい」を 2 回重ねる）
**直し方の案**: 「group でドメインレイヤーを表すと、どの Role が同じ層かが読み取れます。粒度は、読んで分かる大きさにそろえてください。」

## 直さない: 別モジュールと書くが、依存の足し方に触れていない（basic-api）
- decision: 直さない
- file: ja/guides/basic-api.mdx
- quote: は別モジュール `katachi-konsist` に入っています
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: 一旦そのまま

元の指摘:

**ルールに反する点**: C1（`katachi-konsist` の依存と `assert(FileConstraintCheck())` をそろえて案内する）。依存の行 `testImplementation("me.tbsten.katachi:katachi-konsist:<version>")` は kt-file-content.mdx L96〜100 にだけあり、このページは直後（L244）の kt-file-content へのリンクに任せている
**直し方の案**: 「依存の足し方は kt-file-content のガイドにあります」と一言添える

## 直さない: 抽象的な効果の文
- decision: 直さない
- file: ja/guides/diff-report.mdx
- quote: レビュー体験を向上させることができます
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: まあここはまとめ的な一文だし多少抽象的でええのかなと

元の指摘:

**ルールに反する点**: D3-15（何が起きるかを書く。D3-7 と同じ方向）。根拠: 何が良くなるのか書いていない
**直し方の案**: 「CI やレビュー補助の Agent Skill で `katachiDiff` を実行すると、レビューする人が最初に変更された Role の一覧を読めます。」

## 直さない: 「このように」が指すものが次の画像
- decision: 直さない
- file: ja/guides/diff-report.mdx
- quote: 重みの有無で Importance 列の見え方がこのように変わります。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: 提案してもらった方が逆に違和感に見える

元の指摘:

**ルールに反する点**: D3-3（指示語ではなく指すものを書く）。根拠: 画像を見ないと何が変わるか分からない。decisions の指示語 6 件とは別の箇所
**直し方の案**: 「重みの有無で Importance 列の色の濃さが変わります（下の画像は同じ差分を重みなしと `diffWeight = 10.0` で比べた例です）。」

## 直さない: 指示語「こちら」
- decision: 直さない
- file: ja/guides/document-generation.mdx
- quote: こちらも参考にしてください。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-3（指すものを書く）。節の頭が LinkCard の直前なので、指すのは `sample/` の生成物
**直し方の案**: 「`sample/` の生成物も参考にしてください。」

## 直さない: README を作る Role に `documented = false` が無い
- decision: 直さない
- file: ja/guides/generate-code-from-template.mdx
- quote: "FeatureReadme".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: このページで話したいこととは関係ない話になっちゃうので直さないでいいかな

元の指摘:

**ルールに反する点**: B30（生成ドキュメントで読まれないファイル、単独の README などの Role には `documented = false` を付ける。推奨）
**直し方の案**: `documented = false` を 1 行足す。載せたいなら直さない

## 直さない: README を作る Role に `documented = false` が無い（まとめの例・要確認）
- decision: 直さない
- file: ja/guides/generate-code-from-template.mdx
- quote: "FeatureReadme".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B30
**直し方の案**: L174 と同じ

## 直さない: 2 行だけの表「type 宣言の詳細を指定する」
- decision: 直さない
- file: ja/guides/kt-file-content.mdx
- quote: <td>やりたいこと</td>
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-11（表にするのは複数のものを複数の観点で比べるときだけ）。「やりたいこと」→「例」の 2 行 × 1 観点
**直し方の案**: 見出し付きの 2 つのコード例にする

## 直さない: 「戻り値を指定」の行の「補足」が空
- decision: 直さない
- file: ja/guides/kt-file-content.mdx
- quote: <td>
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-11（表は複数の観点で比べるときだけ。ここは補足の列が 1 行分空で、もう 1 行だけの表）。L337 の「関数の引数・戻り値の定義」の表の、2 行目の補足セル（L375〜L377）
**直し方の案**: 補足が要らないなら列を外す、または「戻り値の型を `.returns("…")` で指定します」と 1 行足す

## 直さない: 太字が多い
- decision: 直さない
- file: ja/guides/kt-file-content/kotlin-mapping.mdx
- quote: **違反** … 書いていない宣言は違反になります
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: ここは太字じゃないと要点が分かりにくくなって、むしろ読みにくくなりそう

元の指摘:

**ルールに反する点**: D3-15（過剰な太字）。**違反**・**問わない**・**問いません** が各節の導入文に繰り返し出る（L22・L23・L27・L214・L294・L395・L465・L543）
**直し方の案**: 太字は L22・L23 の用語の定義だけにし、各節の導入文は地の文にする

## 直さない: IDE 設定の Role が生成ドキュメントに出る形のまま
- decision: 直さない
- file: ja/guides/layout.mdx
- quote: "IdeCodeStyle".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B30（ツールの設定ファイルの Role）
**直し方の案**: `documented = false` を足すか、直さない

## 直さない: ファイルの種類が生成ドキュメントに出る形のまま（GeneratedFile）
- decision: 直さない
- file: ja/guides/layout.mdx
- quote: "GeneratedFile".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B30（生成物の Role には `documented = false` を付ける。推奨）
**直し方の案**: `documented = false` を 1 行足す。または「生成ドキュメントに出さないときは `documented = false`」とコメントを添える

## 直さない: ファイルの種類が生成ドキュメントに出る形のまま（BuildOutput）
- decision: 直さない
- file: ja/guides/layout.mdx
- quote: "BuildOutput".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B30
**直し方の案**: 上と同じ

## 直さない: 指示語「いずれも」
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: いずれもプロジェクトのアーキテクチャ定義を受け取り、検証や生成に使っています。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: まあ伝わるからいいかな

元の指摘:

**ルールに反する点**: D3-3（指すものを書く）。直前の表を指す。削る案は L63〜64 の件と同じ
**直し方の案**: 「6 つの processor はいずれも」と書くか、削る

## 直さない: processor の例が断片（import・opt-in 無し、省略の断りも無い）
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: object CountFilesPerRole : ArchitectureProcessorNoArg<Map<String, Int>> {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D2-2。同じ指摘が L234（quote: `object CountFilesPerRole : ArchitectureProcessor<CountFilesPerRole.Args, Map<String, Int>> {`）と L263（quote: `// metadata のキー`）にもある（場所ごとに別件）
**直し方の案**: `// package / import の行は省略` を各ブロックの先頭に足す。断片は書かない決まり（D2-1）なので、直さない判断もありえる

## 直さない: 同上（引数ありの例）
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: object CountFilesPerRole : ArchitectureProcessor<CountFilesPerRole.Args, Map<String, Int>> {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D2-2（L204 と同じ）
**直し方の案**: L204 と同じ

## 直さない: 同上（Role の metadata の例）
- decision: 直さない
- file: ja/guides/processor.mdx
- quote: // metadata のキー
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D2-2（L204 と同じ）。この例は `MetadataKey`・`metadata()` の import も要る
**直し方の案**: L204 と同じ

## 直さない: 「それぞれを」が何を指すか書いていない
- decision: 直さない
- file: ja/guides/role.mdx
- quote: それぞれを、もう少し詳しく説明します。
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-3（指示語ではなく指すものを書く）。「それぞれ」は直前の 1 つの例の title / summary / layout / ktFileContent を指す
**直し方の案**: 「title・summary・layout・ktFileContent を、1 つずつ詳しく説明します。」

## 直さない: 別モジュールと書くが、依存の足し方に触れていない（role）
- decision: 直さない
- file: ja/guides/role.mdx
- quote: は別モジュール `katachi-konsist` に入っています
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: C1（上と同じ）。L209 に kt-file-content へのリンクはあるが、この段落とは離れている
**直し方の案**: 上と同じ

## 直さない: 非生物主語「〜が分かれてしまいます」（Role を分ける理由）
- decision: 直さない
- file: ja/guides/role.mdx
- quote: 説明・制約・テンプレートが「どちらのファイルの話か」で分かれてしまいます
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D3-15（非生物主語）
**直し方の案**: 「1つの Role にまとめると、説明・制約・テンプレートのそれぞれで『どちらのファイルの話か』を書き分けることになります。」

## 直さない: Role の関数を group なしで ProjectArchitecture.kt から呼んでいる
- decision: 直さない
- file: ja/guides/role.mdx
- quote: useCase()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: まあドキュメントだしギリ許容かな

元の指摘:

**ルールに反する点**: B20（`ProjectArchitecture.kt` は group の関数を呼ぶだけ。Role のファイルは `<group>/<Role>Role.kt`、group の外は直下）。分割後の例は `domain/` の下に Role のファイルを置くのに、`Domain` group が無い
**直し方の案**: 分割後の例も `"Domain".group { useCase() ... }` にする。または直さない

## 直さない: group を ProjectArchitecture.kt の中に直接書いている（role のまとめ）
- decision: 直さない
- file: ja/guides/role.mdx
- quote: "Domain".group {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt` の関数にして、`ProjectArchitecture.kt` はその関数を呼ぶだけ）
**直し方の案**: `domain/DomainGroup.kt` を足す。または直さない

## 直さない: ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: title=roles/ClaudeCode.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる
同じ指摘: ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## 直さない: ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: title=roles/Cursor.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる
同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## 直さない: ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: title=roles/Windsurf.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる
同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## 直さない: ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: title=roles/GitHubCopilot.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる
同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## 直さない: ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: title=roles/Cline.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる
同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## 直さない: ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: title=roles/Junie.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる
同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## 直さない: ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: title=roles/Codex.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる
同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## 直さない: ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: title=roles/Antigravity.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる
同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L707

## 直さない: ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- decision: 直さない
- file: ja/recipes/ai-agent.mdx
- quote: title=roles/Aider.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる
同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636

## 直さない: android-three-layer: Role の定義ファイル名が B20 の形でない
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: title=roles/UiState.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする
同じ指摘: ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## 直さない: android-three-layer: Role の定義ファイル名が B20 の形でない
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: title=roles/ViewModel.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする
同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## 直さない: android-three-layer: Role の定義ファイル名が B20 の形でない
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: title=roles/Screen.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする
同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## 直さない: android-three-layer: Role の定義ファイル名が B20 の形でない
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: title=roles/UseCase.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする
同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## 直さない: android-three-layer: Role の定義ファイル名が B20 の形でない
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: title=roles/DomainModel.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする
同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## 直さない: android-three-layer: Role の定義ファイル名が B20 の形でない
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: title=roles/RepositoryInterface.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする
同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## 直さない: android-three-layer: Role の定義ファイル名が B20 の形でない
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: title=roles/RepositoryImplementation.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする
同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L325

## 直さない: android-three-layer: Role の定義ファイル名が B20 の形でない
- decision: 直さない
- file: ja/recipes/android-three-layer.mdx
- quote: title=roles/DataSource.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする
同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294

## 直さない: detekt: group と 2 つの Role を 1 つの `roles/Detekt.kt` に入れている（B20・要確認）
- decision: 直さない
- file: ja/recipes/detekt.mdx
- quote: // roles/Detekt.kt
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）
**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `detekt/DetektGroup.kt`

## 直さない: add-a-role の Worker の例に省略の断りが無い
- decision: 直さない
- file: skills/katachi/references/add-and-update/add-a-role.md
- quote: "Worker".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: D2-2（断片の例で package / import を省くなら、省いたと書く）
**直し方の案**: 例の前に「(imports omitted)」と一言添える。add-and-update は既存の定義へ足す作業なので、ファイルの先頭の import は利用者のファイルにすでにある、で足りるとも読める（要確認）

## 直さない: 6-B の CI の yaml の Role の例に documented = false が無い
- decision: 直さない
- file: skills/katachi/references/setup/step6/6-B-ci.md
- quote: ".github/workflows" / "*.yml".file()
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B30（CI の yaml など、ツールの設定の Role には `documented = false` を付ける。推奨）
**直し方の案**: 「(for example …; with `documented = false`)」と添えるか、add-a-role への参照を 1 つ足す。そのままでもよい

## 直さない: 6-C の ArchitectureDocs の例に documented = false が無い
- decision: 直さない
- file: skills/katachi/references/setup/step6/6-C-documentation-generation.md
- quote: "ArchitectureDocs".role {
- decided: 2026-11-11 261110-01 KATACHI-365
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

**ルールに反する点**: B30（生成物の Role には `documented = false` を付ける。推奨）。同じ例に D2-2（断片の例で package / import を省いたと書く）も
**直し方の案**: `description = …` の次に `documented = false` を足す。例の前に「package / import の行は省略」と一言添える

## このまま: ドキュメント生成の出力先を build/ の外にしてコミットすると [UnexpectedFile] になる
- decision: このまま
- file: docs/src/content/docs/ja/guides/document-generation.mdx
- quote: outputDir
- decided: 2026-11-12 261112-01 KATACHI-368
- by: 利用者（スレッド）

利用者の一言: 「これは前々から許容してた」

元の指摘: 生成したページを build/ の外に出してコミットすると、それを宣言する Role が無いので検査が [UnexpectedFile] で落ちる。Role を宣言するまでは落ちるのが既定の挙動として受け入れ済み。

## 後で: ドキュメント生成の出力先にある、katachi が作っていない *.md を消す
- decision: 後で
- file: katachi/src/main/kotlin/me/tbsten/katachi/docs/internal/DocumentFiles.kt
- quote: the directory must belong to katachi alone
- decided: 2026-11-12 261112-01 KATACHI-368
- by: 利用者（スレッド）
- revisit: KATACHI-370（katachi が作っていない .md を黙って消さない）に手を付けたとき。v0.4 では対応しない

利用者の一言: 「v0.4 では対応しないけど後々改善したいので、時期みていチケット切っといて」

元の指摘: 出力先を「katachi 専用」として扱い、書き出すたびに今回作らなかった *.md を消す（v0.2.0 からの挙動）。手書きの docs/ を出力先にすると docs/guide.md なども消える。ログに Removed … は出るが止まらない。

## 直さない: 出力先を変える例のとおりにすると、次のテストが生成ページの数だけ違反になる（出力先の決まりが書かれていない）
- decision: 直さない
- file: ja/guides/document-generation.mdx
- quote: 出力先は、build.gradle.kts で `katachi.processors.docs.outputDir` を設定すると変更できます。
- decided: 2026-11-12 261112-01 KATACHI-372
- by: 利用者（Review の popup）

利用者の一言: KATACHI-370で別途検討なので一旦は個別では直さない（他も同様）

元の指摘:

「出力先ディレクトリ」の節は、outputDir で出力先を変えられる、までしか書いていない。実装には決まりが 3 つある。(1) 出力先の中の、今回生成しなかった `*.md` は削除される（katachi/src/main/kotlin/me/tbsten/katachi/docs/internal/DocumentFiles.kt:9-16）。(2) build/ の外に出すと生成ページが gitTracked() の対象になり、Role を宣言しないと `[UnexpectedFile]` になる。(3) `--arg mode=check` は書かずに比べ、古いページや余分な `*.md` があると失敗する。動かして確かめた: ページの例のとおり docs/architecture に出すと、次の `:architecture-test:test` が生成ページの数だけ `[UnexpectedFile]` で失敗し、出力先に置いた手書きの `*.md` は `Removed …` のログとともに消えた。この 3 つは skill の 6-C と 6-B にだけ書いてある。英語のページも同じ。
- 直し方の案: 節に 3 点を足す。(1) 出力先のディレクトリは katachi のものになり、生成しなかった `*.md` は消える。(2) コミットするなら生成ページの Role を宣言する。(3) CI では `--arg mode=check` で古さを検出できる
- 出どころ: 手順 5 の 4（動かして確定。priority 4 → 6）。結果は /home/claude/katachi/.local/release-v0.4.0/tmp/docs-vs-impl/run-results.md の 5

## 直さない: 比較表は Konture を「統合あり」と書くが、使い方のページが docs に無い（E-a・要確認）
- decision: 直さない
- file: ja/get-started/comparison-with-other-tools.mdx
- quote: 統合あり（`katachi-konture`）
- rule: E-a（docs/AGENTS.md の木 L85「Konture との統合」のページが 2d43371f で消えていて、サイドバーにも無い）
- decided: 2026-11-12 261112-01 KATACHI-372
- by: 利用者（Review の popup）

利用者の一言: なし

元の指摘:

比較表は Konture を「🆗 統合あり（katachi-konture）」と書く。使い方のページ（guides/konture-integration）は 2d43371f で外され、今の ja のページで Konture に触れるのはこのページだけ。モジュール katachi-konture は settings.gradle.kts にある。AGENTS の木（L85）にはページが残ったまま。担当の get-started 4 ページと roadmap は、木とサイドバー（docs/astro.config.mjs）で一致している。showcase.mdx（draft: true）は木にもサイドバーにも無い。要確認。
- 直し方の案: AGENTS の木から L85 を消す。表は「統合あり」のまま API リファレンスへのリンクを付けるか、案内先ができるまで「🆗 可能」にするかを利用者が決める。
- 種類: ガイド・ルールの側の食い違い（E）
