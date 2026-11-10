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
