---
title: v0.4.0 リリース前チェック 5 回目 docs の指摘（手順 5・9-2・13）
base: 79ab282a
---

## [p7] katachi-script のページに、失敗を終了コードで返す書き方が無い
- file: ja/guides/cli-and-script/katachi-script.mdx
- line: 112
- quote: ## 記述スタイル
- impact: あり: CI に `katachi ci.katachi.kts` を書くと、中のコマンドが失敗しても終了コード 0 で終わり、CI が緑になる
- recommend: 直してもらう 10, 直さない 0

- 該当: `execute()`・`executeCommands()` は終了コードを返すだけで、`exitProcess` を書かないとスクリプトは 0 で終わる。書き方は KDoc（`commands.kt` の Example 1）にしか無い
- 利用者の判断: 2026-11-20 22:35 に TODO.html の (b)「docs の katachi-script のページに `exitProcess(executeCommands(…))` の書き方を足す」を選んだ（katachi の挙動は変えない。decisions/process.md に記録）
- 直し方の案: 「記述スタイル」の両方のタブに、CI で使うときの形を 1 つ足す。`kotlin.system.exitProcess(executeCommands(katachi.doctor, katachi.scaffold))`（失敗したコマンドの終了コードでスクリプトを終える）。en も同じ位置に訳す
- 出どころ: 探索的検証 1 周目 CLI/script

## [p6] Role のページの切り出し例をそのまま貼ると、import と @file:OptIn が足りずコンパイルできない
- file: ja/guides/role.mdx
- line: 387
- quote: fun DeclarationContainerScope.useCase() = "UseCase".role {
- impact: あり: 写した人が Unresolved reference の羅列に当たり、足す import がどのページにも書いていない
- recommend: 直してもらう 8, 直さない 2

- 該当: 「Role 定義を分割する」「まとめ」の `fun DeclarationContainerScope.useCase()` / `private fun RoleScope.useCaseFileContent()` に import も `@file:OptIn(ExperimentalKatachiApi::class)` も無い。`RoleScope`・`ktFileContent`・`FileConstraintCheck` の import 先は docs のどこにも無い
- 再現: 「まとめ」の UseCaseRole.kt を検証モジュールに貼って `:architecture-test:compileTestKotlin` → `RoleScope`・`ktFileContent`・`package`・`import`・`required` … の Unresolved reference
- 直し方の案: 切り出し例の直前に「必要な import」を 1 つのコードブロックで置く（`@file:OptIn` も含める）。`import me.tbsten.katachi.dsl.RoleScope`・`import me.tbsten.katachi.konsist.declarations.ktFileContent`・`import me.tbsten.katachi.check.FileConstraintCheck`
- 同じ指摘: ja/guides/kt-file-content.mdx の「インストール」
- 出どころ: 探索的検証 1 周目 docs

## [p6] ktFileContent のページに、要る import が書かれていない
- file: ja/guides/kt-file-content.mdx
- line: 94
- quote: ## インストール
- impact: あり: 例を写すと ktFileContent・FileConstraintCheck が Unresolved reference になる
- recommend: 直してもらう 8, 直さない 2

- 該当: 「インストール」の節に依存の書き方はあるが、`import me.tbsten.katachi.konsist.declarations.ktFileContent` と `import me.tbsten.katachi.check.FileConstraintCheck` が無い
- 直し方の案: この節に 2 つの import を一度だけ書く
- 同じ指摘: ja/guides/role.mdx の切り出し例
- 出どころ: 探索的検証 1 周目 docs

## [p5] v0.3 から上げる人が、サイトで移行の手がかりに行き着けない
- file: docs/astro.config.mjs
- impact: あり: `.module { }` の削除など 0.3 → 0.4 の破壊的変更を踏んだ人が、Before / After の表に行き着けない
- recommend: 直してもらう 7, 直さない 3

- 該当: サイト（ja・en）に「移行」「リリースノート」「変更履歴」のページが無く、ヘッダー・サイドバー・フッターにも GitHub の Releases への案内が無い。Migration の表はリリースノート（GitHub Releases）にしか無い。サイト内検索で「移行」は 0 件
- 直し方の案: サイドバーかヘッダーに GitHub Releases へのリンク（または版ごとの移行ページ）を足す。バージョン切替が v0.3・v0.2 を持っているので、その近くに「0.3 → 0.4 の移行」
- 出どころ: 探索的検証 1 周目 docs

## [p4] katachi { architecture = "…" } が何を指すかが書かれておらず、打ち間違いは生成ファイルのコンパイルエラーになる
- file: ja/get-started/manual-install.mdx
- line: 69
- quote: architecture = "com.example.projectArchitecture"
- impact: あり: package や変数名を変えた人が、自分で書いていないファイルの Unresolved reference で詰まる
- recommend: 直してもらう 7, 直さない 3

- 該当: 手順 2 は「パッケージ名を変更してください」とだけ言う。この設定が `katachiDocs` などのタスクが定義を探す場所で、`<package>.<プロパティ名>` の形であることが書かれていない
- 再現: 値を `"com.example.projectArchitectur"` にして `:architecture-test:test` → `build/generated/…/GeneratedKatachiEntryPoint.kt:8:20 Unresolved reference 'projectArchitectur'`
- 直し方の案: 手順 2 に 1 行（「`<package>.<val の名前>` と一致させる。違うと生成ファイルのコンパイルエラーになる」）
- 出どころ: 探索的検証 1 周目 docs

## [p4] Baseline のページが勧める構成で、katachiDocs の出力に中身の無いページができる
- file: ja/guides/baseline.mdx
- line: 78
- quote: documented = false // ツールが書くファイルなので、生成ドキュメントには載せない
- impact: あり: ページどおりに書いて katachiDocs を走らせると、見出しだけの空ページとそこへのリンクが残る
- recommend: 直してもらう 6, 直さない 4

- 該当: `"Tool".group { "Katachi".group { baselineFile() } }` の中に `documented = false` の Role を 1 つだけ置く形。group は中身が空でも出力され、`Tool/Katachi/README.md` が `# Katachi` の 1 行だけになる（132 行目の完成形も同じ）
- 直し方の案: 例の group にも `documented = false` を付ける（これで空ページが消えるかは未確認）。katachiDocs 側で空の group を出さない直し方もある
- 出どころ: 探索的検証 1 周目 docs

## [p4] 導入のチェックリストのサンプルで、meta.workdir が絶対パスになっている
- file: docs/public/install/ja/check-list-sample.html
- line: 1363
- quote: "workdir": "/Users/me/dev/example-app/.local/install-katachi
- impact: あり: skill の 0-2 は meta.workdir をプロジェクトのルートからの相対パスと決めており、サンプルを真似ると 6 の報告のリンク（file://<meta.projectRoot>/<meta.workdir>）が壊れる
- recommend: 直してもらう 7, 直さない 3

- 該当: 表示用のデータの `meta.workdir` が絶対パス（後ろに括弧書きの注記も入っている）。同じファイルの例のコメント（1565 行）は `.local/install-katachi` で、skill の 0-2 と合っている
- 直し方の案: `.local/install-katachi` にする（注記は別のキーか本文へ）
- 同じ指摘: docs/public/install/en/check-list-sample.html の 1363 行
- 出どころ: 9-2 の手順書レビュー担当

## [p4] 導入のチェックリストのサンプルで、meta.workdir が絶対パスになっている
- file: docs/public/install/en/check-list-sample.html
- line: 1363
- quote: "workdir": "/Users/me/dev/example-app/.local/install-katachi"
- impact: あり: skill の 0-2 は meta.workdir を相対パスと決めており、サンプルを真似ると 6 の報告のリンクが壊れる
- recommend: 直してもらう 7, 直さない 3

- 直し方の案: `.local/install-katachi` にする
- 同じ指摘: docs/public/install/ja/check-list-sample.html の 1363 行
- 出どころ: 9-2 の手順書レビュー担当

## [p3] katachi コマンドのページに lint と .katachi.kts の節が無く、既定値の説明も古い
- file: ja/guides/cli-and-script/cli.mdx
- line: 23
- quote: ## コマンドリスト
- impact: あり: skill の 0-1 がこのページを案内するが、手順で使う katachi lint とスクリプトの実行が載っていない
- recommend: 直してもらう 7, 直さない 3

- 該当: 節は doctor・scaffold・agent だけ。`katachi lint` と `katachi <file>.katachi.kts` が無い。scaffold の既定値の列は、チェックリストの値を先に使い、無ければ検出した値を使う今の動きと合っていない
- 直し方の案: lint と .katachi.kts の節を足し、既定値の列を「チェックリストの値 → 検出した値」にする
- 出どころ: 9-2 の手順書レビュー担当

## [p3] CLI のページに、doctor の --lang と出力の言語の決め方が無い
- file: ja/guides/cli-and-script/cli.mdx
- line: 36
- quote: katachi doctor
- impact: あり: katachi-script のページが lang の例を見せて「詳細は CLI のページ」と言うのに、CLI のページで見つからない
- recommend: 直してもらう 6, 直さない 4

- 該当: doctor の節は `katachi doctor` だけで、オプションの表は scaffold の分しかない。実装は `katachi doctor [--lang <en|ja>]` で、言語は `--lang` → チェックリストの言語 → `KATACHI_LANG` → ロケールの順に決まる
- 直し方の案: doctor の節に `--lang <en|ja>` と決まり方を 1 行
- 出どころ: 手順 5（docs と実装の食い違い）

## [p3] SARIF を有効にする方法を書いたページが無い
- file: ja/guides/baseline.mdx
- line: 97
- quote: report { sarif = true }
- impact: あり: SARIF を使いたい人が、設定の書き方を API リファレンスまで探しに行く
- recommend: 直してもらう 5, 直さない 5

- 該当: SARIF の話は baseline のページのこの一文だけ。`katachi { report { sarif = true } }` で test タスクが `build/reports/katachi/katachi.sarif` を書くことは、Gradle plugin の Javadoc にしか無い（v0.3.0 からの状態）
- 直し方の案: SARIF の節を足すか、この文に API リファレンスへのリンクを足す（機能を前に出すかは人が決める）
- 出どころ: 手順 5（docs と実装の食い違い）

## [p3] ガイドと API リファレンスが互いに案内していない
- file: ja/guides/basic-api.mdx
- impact: あり: anyFile()・ignore()・FileConstraintCheck などの正確な宣言を探す人が、ガイドから API リファレンスに行けない
- recommend: 直してもらう 6, 直さない 4

- 該当: API リファレンスへのリンクがあるのは document-generation・processor・github のレシピ・roadmap だけで、基本的な API・Role・Layout・ktFileContent のページには無い。逆向き（API リファレンスからガイドへ）も無い
- 直し方の案: 各ガイドの末尾か「次のステップ」に、該当パッケージ（`dsl`・`check`・`konsist.declarations`）の API リファレンスへのリンク
- 出どころ: 探索的検証 1 周目 docs、手順 6（API リファレンスの巡回）

## [p3] レシピの一覧の Markdown 版が空で、エージェントが一覧を読めない
- file: ja/recipes/index.mdx
- line: 12
- quote: <SectionIndex dir="recipes" />
- impact: あり: ページの Markdown 版（recipes.md）は見出しと空行だけで、.md 版を読むエージェントにはレシピの一覧が見えない
- recommend: 直してもらう 6, 直さない 4

- 該当: 一覧を `<SectionIndex>` が組み立てるので、Markdown 版には何も出ない（`/katachi/recipes.md` は 47 バイト）。skill は「ページは .md 版を読む」と案内するようになった（PR #120）が、この 1 ページだけ HTML を読むよう例外にしている
- 直し方の案: 一覧を本文にも書く、または .md の生成で SectionIndex を一覧に置き換える
- 出どころ: KATACHI-409 の確認中に見つけた

## [p2] 「gh skills」のタブ名・本文と、コマンド gh skill install が合っていない
- file: ja/get-started/first-architecture.mdx
- line: 38
- quote: <TabItem label="gh skills">
- impact: なし
- recommend: 直してもらう 6, 直さない 4

- 該当: タブ名と 35 行目の本文は `gh skills`、タブの中のコマンドは `gh skill install …`（単数）。英語版も同じ
- 直し方の案: 本文とタブ名を `gh skill` にそろえる
- 出どころ: 探索的検証 1 周目 docs

## [p1] サイドバーの「初めての定義」と、ページの題名「初めてのアーキテクチャ定義」が違う
- file: docs/astro.config.mjs
- line: 37
- quote: ja: '初めての定義'
- impact: なし
- recommend: 直さない 5, 直してもらう 5

- 該当: サイドバーと「手動インストール」の次のステップは「初めての定義」、ページの題名とほかのページの案内は「初めてのアーキテクチャ定義」
- 直し方の案: どちらかにそろえる
- 出どころ: 探索的検証 1 周目 docs

## [p1] 言語を切り替えると、日本語の見出しのアンカーが英語ページにそのまま付く
- file: ja/guides/kt-file-content/kotlin-mapping.mdx
- quote: 修飾子
- impact: なし
- recommend: 直さない 7, 直してもらう 3

- 該当: `/ja/guides/kt-file-content/kotlin-mapping/#修飾子` で English に切り替えると、英語ページに `#修飾子` が付いたまま先頭に出る（`#modifiers` なら届く）
- 直し方の案: 言語切替でアンカーを引き継がない（Starlight の言語切替の挙動）
- 出どころ: 探索的検証 1 周目 docs
