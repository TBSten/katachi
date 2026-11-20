---
title: v0.4.0 リリース前チェック 5 回目 手順 2 の追いつき（agy の check-ja）
base: f520b26c
---

## [p2] 1 つの文に「katachi」が 2 回出てくる
- file: ja/get-started/first-architecture.mdx
- line: 23
- quote: katachi には AI Agent driven で katachi のセットアップを支援する
- impact: なし
- recommend: 直してもらう 6, 直さない 4

- 該当: 「katachi には AI Agent driven で katachi のセットアップを支援する CLI, Agent Skill が用意されています」
- 直し方の案: 「katachi には、セットアップを AI Agent に任せるための CLI と Agent Skill が用意されています」
- 出どころ: 手順 2 の追いつき（Mac の agy `check-ja`）

## [p2] 「お勧め」と「おすすめ」が混ざっている
- file: ja/get-started/first-architecture.mdx
- line: 58
- quote: 管理しておくことをお勧めします
- impact: なし
- recommend: 直してもらう 7, 直さない 3

- 該当: 58 行目だけ「お勧め」。16 行目と 70 行目は「おすすめ」
- 直し方の案: 「おすすめします」にそろえる
- 出どころ: 手順 2 の追いつき（Mac の agy `check-ja`）

## [p2] 「インストールチェックリストと呼ばれる HTML ファイル」の前後に余計な空白がある
- file: ja/get-started/first-architecture.mdx
- line: 82
- quote: 情報は インストールチェックリストと呼ばれる HTML ファイル に出力される
- impact: なし
- recommend: 直してもらう 7, 直さない 3

- 該当: 「情報は␣インストールチェックリストと呼ばれる HTML ファイル␣に」。日本語の語と語の間に空白が入っている
- 直し方の案: 空白を取る。用語として目立たせたいなら「**インストールチェックリスト**と呼ばれる HTML ファイル」と太字にする
- 出どころ: 手順 2 の追いつき（Mac の agy `check-ja`）

## [p2] 「掲載する」の主語が無い
- file: ja/get-started/first-architecture.mdx
- line: 86
- quote: AI Agent が判断に迷った箇所を
- impact: なし
- recommend: 直してもらう 5, 直さない 5

- 該当: 「インストールチェックリスト内には AI Agent が判断に迷った箇所を **質問形式で掲載** する場合があります」。誰が掲載するのかが読み取りにくい
- 直し方の案: 「インストールチェックリストには、AI Agent が判断に迷った箇所が **質問の形で載る** ことがあります」
- 出どころ: 手順 2 の追いつき（Mac の agy `check-ja`）

## [p2] 「生成したら」は「生成した」の打ち間違いに見える
- file: ja/get-started/first-architecture.mdx
- line: 120
- quote: AI Agent が生成したらアーキテクチャ定義をレビューして
- impact: なし
- recommend: 直してもらう 7, 直さない 3

- 該当: 「AI Agent が生成したらアーキテクチャ定義をレビューして」。「生成したら、定義を」とも「生成した定義を」とも読めて、一度つまずく
- 直し方の案: 「AI Agent が生成したアーキテクチャ定義をレビューして」
- 出どころ: 手順 2 の追いつき（Mac の agy `check-ja`）

## [p1] 「AI Agent」と書くページで、ここだけ「Agent」になっている
- file: ja/get-started/first-architecture.mdx
- line: 80
- quote: プロンプトを受け取ると Agent は
- impact: なし
- recommend: 直してもらう 5, 直さない 5

- 該当: ページのほかの箇所は「AI Agent」。80 行目と 82 行目だけ「Agent」（「katachi Agent Skill」は名前なのでそのまま）
- 直し方の案: 「AI Agent は」にする
- 同じ指摘: 82 行目の「Agent が収集した情報は」
- 出どころ: 手順 2 の追いつき（Mac の agy `check-ja`）

## [p1] 「AI Agent」と書くページで、ここだけ「Agent」になっている（82 行目）
- file: ja/get-started/first-architecture.mdx
- line: 82
- quote: Agent が収集した情報は
- impact: なし
- recommend: 直してもらう 5, 直さない 5

- 該当: 80 行目と同じ揺れ
- 直し方の案: 「AI Agent が収集した情報は」にする
- 同じ指摘: 80 行目の「プロンプトを受け取ると Agent は」
- 出どころ: 手順 2 の追いつき（Mac の agy `check-ja`）

## [p1] 「押下」「返却」が硬い
- file: ja/get-started/first-architecture.mdx
- line: 88
- quote: コピーボタンを押下して AI Agent に回答を返却しましょう
- impact: なし
- recommend: 直してもらう 6, 直さない 4

- 該当: 手順のページの中で、ここだけ書き言葉の硬い語。107 行目は同じ操作を「AI Agent に渡す」と書いている
- 直し方の案: 「コピーボタンを押して、回答を AI Agent に渡しましょう」
- 出どころ: 手順 2 の追いつき（Mac の agy `check-ja`）

## [p1] 1 つの文に「チーム」が 3 回出てくる
- file: ja/get-started/first-architecture.mdx
- line: 125
- quote: チームで開発している場合は、
- impact: なし
- recommend: 直さない 6, 直してもらう 4

- 該当: 「チームで開発している場合は、**チームメンバー（特にチームのリード）にも重点的にレビュー** してもらいましょう」
- 直し方の案: 「チームで開発している場合は、**メンバー（特にリード）にも重点的にレビュー** してもらいましょう」
- 出どころ: 手順 2 の追いつき（Mac の agy `check-ja`）

## [p1] 見出しの「(オプション)」だけ半角のかっこ
- file: ja/get-started/first-architecture.mdx
- line: 128
- quote: CI に組み込む (オプション)
- impact: なし
- recommend: 直さない 5, 直してもらう 5

- 該当: 同じページの本文（62・107・125 行目）は全角の「（）」
- 直し方の案: 「（オプション）」にする。見出しのアンカーが変わるが、このアンカーへのリンクは docs に無い（grep で確認）
- 出どころ: 手順 2 の追いつき（Mac の agy `check-ja`）
