---
title: v0.4.0 リリース前チェック 3 回目（261112-01）手順 11
base: 0a0eaf97
---

## [p6] 対応表の inline fun run(noinline …) を、右の列の行が許可していない（inline が無い）
- file: ja/guides/kt-file-content/kotlin-mapping.mdx
- line: 285
- quote: allow(`fun`, "run")("noinline block: () -> Unit")
- impact: あり: 表のとおりに書くと inline fun が違反になる。違反のメッセージから直せる
- recommend: 直してもらう 9, 直さない 1

今回、左の列（L278）に `inline fun run(noinline block: () -> Unit) {}` を足したが、右の列の L285 は `inline` を書いていないので、この行は左の例に一致せず違反になる。同じページの L373 は ``allow(inline, `fun`, "get*")`` と書いている。実装でも inline は余分な修飾子に数えられる（katachi-konsist の DeclarationMatching.kt の extraModifiersOf。除くのは public と final だけ）。手順 5 にも回す。

- 破っている項目: C4（書いていない修飾子が付いた宣言は違反になる。public と final を除く）。根拠: 同じページの L214 と L20（「ktFileContent の行」の列は左の Kotlin の例を許可する行）
- 直し方の案: ``allow(inline, `fun`, "run")("noinline block: () -> Unit")`` にする
- 種類: ルールに反する箇所

## [p5] 検証モジュールの Role の例が catch-all で、同じファイルの「catch-all の Role を書かない」に反する
- file: skills/katachi/references/setup/step3/3-2-write-the-definition.md
- line: 136
- quote: "architecture-test" / testSourceSet / kotlin / "**" / "*".ktFile()
- impact: あり: エージェントが写し、導入先すべてに「ここにあれば何でも」の Role が入る
- recommend: 直してもらう 8, 直さない 2

前回のあとに足した節の例。検証モジュールの src/test/kotlin の下の Kotlin ファイルを、名前のパターンも中身の制約も無しに全部受け取る。L87 と 3-3 のチェックリストは「名前のパターンか中身の制約を 1 つは付ける」と求め、L142 は 1 つの Role で覆うと書くが例外とは書いていない。sample/android は同じ場所を *Role・*Group・*Test の名前で Role に分けている。

- 破っている項目: S3（skill が自分で述べる決まりに、skill の例が反していない: catch-all の Role を書かない）。根拠: 同じファイルの L87-90「Do not write catch-all Roles」、skills/katachi/references/setup/step3/3-3-refactor.md:14「No catch-all Roles are left」
- 直し方の案: 定義の Role に名前のパターンを付ける（ProjectArchitecture・*Role・*Group）。1 つの Role のままにするなら、L87 と 3-3 の L14 に「検証モジュールの Role は例外」と理由を添えて書く
- 種類: ルールに反する箇所

## [p4] stringParameter("…") と書いていて、文字列がパラメータの名前に読める
- file: skills/katachi/references/add-and-update/use-templates.md
- line: 8
- quote: the `stringParameter("…")` / `booleanParameter` / `intParameter` / `enumParameter` it declares
- impact: あり: デフォルト値を引数名と読み違えると、生成のコマンドが失敗する（エラーから直せる）
- recommend: 直してもらう 8, 直さない 2

同じ文の capture("…") は … が名前。並べて書いた stringParameter("…") も名前と読めるが、引数は default（katachi/src/main/kotlin/me/tbsten/katachi/dsl/TemplateScope.kt:103）で、名前は val のプロパティ名。デフォルト値を --arg の名前として渡すと Unknown processor argument(s) で失敗する。6-D の L57 は stringParameter()（optionally with default = ...）と書いていて、skill の中で形が 2 つある。

- 破っている項目: F3（パラメータは val x by stringParameter() で宣言し、プロパティの名前がパラメータの名前になる）。根拠: guides/generate-code-from-template.mdx:86-119
- 直し方の案: 「the properties declared with `by stringParameter()` / `booleanParameter()` / `intParameter()` / `enumParameter()` (the property's name is the `--arg` name)」
- 種類: ルールに反する箇所

## [p3] AI Agent のレシピへのカードが、リンク先に無い内容（セットアップを任せる方法）を説明している（概要のページ）
- file: ja/guides/cli-and-script.mdx
- line: 47
- quote: description="AI Agent に katachi のセットアップを任せる方法を学びましょう。"
- impact: あり: カードを押しても、説明にある内容がリンク先に無い
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

カード（L44-48、題は「AI Agent と一緒に使う」）は、リンク先 recipes/ai-agent.mdx で「セットアップを任せる方法」を学べると書くが、リンク先は AI コーディングツール 9 つの設定ファイルを group と Role で宣言する例で、その話は無い（手順は get-started/first-architecture.mdx の「AI Agent に任せる」タブにある）。今回 cli.mdx:224 の同じカードだけが「AI Agent の設定ファイルを Role として宣言するレシピです。」に直り、このページは前のまま残った。手順 5・6 にも回す。

- 破っている項目: E（ページ同士の食い違い。D1-14「ページの中身が、木に書かれた位置づけと合う」に近い）。docs/AGENTS.md の「レシピの一覧」は AI Agent のレシピを「ツールごとの group と、設定ファイルの種類ごとの Role」とする
- 直し方の案: このページのユースケースは「katachi を AI Agent にセットアップさせる」なので、カードの行き先を「初めてのアーキテクチャ定義」（/katachi/ja/get-started/first-architecture/）に変え、説明を「AI Agent に katachi のセットアップを任せる手順を確認しましょう。」にする。レシピへのカードのままにするなら、cli.mdx:222-224 と同じ題「AI Agent」・説明「AI Agent の設定ファイルを Role として宣言するレシピです。」にそろえる
- 種類: ガイド・ルールの側の食い違い（E）

## [p3] AI Agent のレシピへのカードが、リンク先に無い内容（セットアップを任せる方法）を説明している（katachi-script のページ）
- file: ja/guides/cli-and-script/katachi-script.mdx
- line: 195
- quote: description="AI Agent に katachi のセットアップを任せる方法を学びましょう。"
- impact: あり: カードを押しても、説明にある内容がリンク先に無い
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

カード（L192-196、題は「AI Agent と一緒に使う」）が g4-01 と同じ説明で recipes/ai-agent.mdx を指している。リンク先にセットアップを任せる話は無い。cli.mdx:224 だけが今回直った。手順 5・6 にも回す。

- 破っている項目: E（ページ同士の食い違い。D1-14 に近い）。g4-01 と同じ
- 直し方の案: g4-01 と同じ。レシピへのカードのままなら、題「AI Agent」・説明「AI Agent の設定ファイルを Role として宣言するレシピです。」にそろえる
- 種類: ガイド・ルールの側の食い違い（E）

## [p3] 「まとめ」の導入文が「生成される Markdown」を予告するが、続くのは Role の定義だけ（要確認）
- file: ja/guides/document-generation.mdx
- line: 529
- quote: 生成される Markdownは次のとおりです。
- impact: あり: 予告された Markdown が続かず、読み手が探す
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

「…を全部使った Role の定義と、生成される Markdownは次のとおりです。」のあとに続くのは Role の定義のコードブロック（L531-547）だけで、生成される Markdown は無い（同じ定義の Markdown は L241-262 にある）。今回この行は「マークダウン」→「Markdown」に言い換えただけで、「Markdown」と「は」の間の半角スペースも落ちた。同じ行の「コード表記でない」は過去の判断どおりで、ここでは扱わない。要確認。

- 破っている項目: D3-5（何と何の話かを、最初の一文で言う）に近い。導入文と続く例が合っていないこと自体はチェックリストに項目が無いので要確認。docs/AGENTS.md「文章の方針」
- 直し方の案: 「title / summary / description / example / layout を全部使った Role の定義は次のとおりです。」にする。Markdown も見せるなら、L241-262 と同じブロックを定義の下に置き、「生成される Markdown は」と半角スペースを入れる
- 種類: ルールに反する箇所

## [p3] Layout のカードの説明が、Layout のガイドに無い内容を挙げている
- file: ja/guides/generate-code-from-template.mdx
- line: 287
- quote: テンプレートを宣言に付ける書き方と、ワイルドカードに名前を付けるキャプチャの使いどころを学びましょう。
- impact: あり: カードを押しても、説明にある内容がリンク先に無い
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

「次のステップ」の Layout のカードが「テンプレートを宣言に付ける書き方」「キャプチャの使いどころ」を学べると書くが、どちらもリンク先の Layout のガイドに無い。2 つのページのカードが互いを指している。

- 破っている項目: E（ガイド同士の食い違い。D1-14「ページの中身が位置づけと合う」に近い）。guides/layout.mdx には .template { } も capture() も出てこない（L207-211 のカードがテンプレートのガイドへ戻しているだけ）
- 直し方の案: 説明を Layout のガイドの中身に合わせる。例:「置き場所の書き方と、任意のファイルを許す・検査から外す宣言の使いどころを学びましょう。」
- 種類: ガイド・ルールの側の食い違い（E）

## [p3] 「プロジェクト定義」は、このページのほかの箇所の「アーキテクチャ定義」と違う呼び方
- file: ja/guides/processor.mdx
- line: 91
- quote: プロジェクト定義に書かれた
- impact: あり: 「プロジェクト定義」がアーキテクチャ定義と別のものに読める
- recommend: 直してもらう 7, 解決済みにする 6, 直さない 2

ビルトインの processor の表の GenerateDocumentation の行だけが「プロジェクト定義」と書く。同じページは L20・L44・L61・L63・L112 などで「アーキテクチャ定義」と書き、「プロジェクト定義」は日本語の docs 全体でここ 1 か所だけ。同じ行への過去の判断（直さない: API 名がコード表記でない（A2）、quote は「summary, description などのプロパティ」）はコード表記の話で、趣旨が違う。

- 破っている項目: A1（katachi の概念は用語で書く。別の言い方で呼び替えない）。docs/AGENTS.md L322-324
- 直し方の案: 「アーキテクチャ定義に書かれた summary, description などのプロパティをもとにドキュメントを生成します。」
- 種類: ルールに反する箇所

## [p3] リンクの文字が、リンク先のページの題（ktFileContent）と違う（E-b・要確認）
- file: ja/recipes/android-three-layer.mdx
- line: 358
- quote: [ファイルの中で定義できる宣言の許可リストを定義する](/katachi/ja/guides/kt-file-content/#インストール)
- impact: あり: リンクの文字と、着いた先のページの題が違う（要確認）
- recommend: 解決済みにする 7, 直してもらう 6, 直さない 3

リンク先 guides/kt-file-content.mdx の title は「ktFileContent」、サイドバーの表示は「概要」。リンクの文字は AGENTS の木に残っている以前の題のままで、着いた先の見出しと合わない。同じ文字のリンクが guides/basic-api.mdx:244 と guides/role.mdx:209 にもある（2-guides-core の担当）。README.ja.md:127 は [ktFileContent] で今の題と合っている。ルールの側（AGENTS の木）が古いのが原因なので E。要確認

- 破っている項目: E-b（docs/AGENTS.md のサイドバーの木 L83 が、今のページの title と違う）。A4（ktFileContent の入れ方はページの間で同じ言い方にする）
- 直し方の案: リンクの文字を今の題に合わせる（例: 「`ktFileContent { }` を使うときは、[ktFileContent のインストール](/katachi/ja/guides/kt-file-content/#インストール)の設定が要ります。」）。あわせて docs/AGENTS.md の木の L83 を今の題に直す
- 種類: ガイド・ルールの側の食い違い（E）

## [p3] 「steps up to 6 are not all done」が、チェックリストの step 6（任意）を含むのか読み取れない
- file: skills/katachi/SKILL.md
- line: 16
- quote: its steps up to 6 are not all done
- impact: あり: 導入済みのプロジェクトで、どの reference を読むかの振り分けを誤りうる（要確認）
- recommend: 直してもらう 7, 直さない 3

前回のあとに足した行。チェックリストの step 6 は任意の 6-1〜6-4（skill の 6-A〜6-D）で、skill の「6. Report the result to the user」にはチェックリストの項目が無い。「steps up to 6」を step 6 まで含むと読むと、導入が済んでいても 6-1〜6-4 が未完了なので、作業用ディレクトリが残っている限りこの行に該当し、定義の更新や違反の修正の依頼まで setup に振り分けられる。ほかのファイルは「steps 0 to 5」と書く。要確認

- 破っている項目: S2（チェックリストの項目 id は、skill の手順と check-list.html で同じ）。根拠: skills/katachi/references/setup/step5/README.md:5（steps 0 to 4、step 6 is optional and does not count）・step6/README.md:35（steps 0 to 5）・step0/0-3-checklist-and-report.md:122
- 直し方の案: 「its items of steps 0 to 5 are not all done (step 6 is optional and does not count)」のように、step5 / step6 の README と同じ言い方にする
- 種類: ルールに反する箇所

## [p3] レポートの記入例が、手で書いたソースのパッケージを ignore する案を推奨している
- file: skills/katachi/assets/ja/check-list.html
- line: 1836
- quote: "当面 ignore する"
- impact: あり: 記入例を見本にしたエージェントが、手で書いたソースを ignore する案を推奨しやすい（要確認）
- recommend: 直してもらう 7, 直さない 4

記入例の cq2 は、app/src/main/kotlin/com/example/app/ui/legacy/（Fragment と Presenter の手書きのソース）について「対象に含めて直す」「当面 ignore する」を並べ、次の行の recommendation で B（ignore）を推奨する。1-2 は「形に迷ったら記入例を見る」と書くので、エージェントは記入例を見本にする。既存の違反を許容する手段として skill が案内するのは baseline（step4/4-2-handle-errors.md:14）。前回から変わっていない例で、en の同じ行（Ignore it for now）も同じ。選ぶのはユーザなので要確認

- 破っている項目: B3（anyFile()・ignore() は機械的に生成されるものと、katachi 以外のツールが構成を決めているものに限る。guides/layout.mdx:114-119）・S3（skill が自分で述べる決まり: skills/katachi/references/add-and-update/README.md:37・fix-violations/README.md:51・SKILL.md:31）
- 直し方の案: 選択肢を「対象に含めて直す」「Role を分けて今の形（Fragment と Presenter）を宣言する」「baseline に入れて、新しい違反だけを検出する」のように、ignore を使わない案にする。recommendation も合わせる。en も同じに直す
- 種類: ルールに反する箇所

## [p3] 古い作業用ディレクトリの扱いが、0-2（使い続ける）と step0 の README（新しく作る）で食い違う
- file: skills/katachi/references/setup/step0/0-2-working-directory.md
- line: 22
- quote: keep using it there
- impact: あり: 古い作業用ディレクトリを見つけたエージェントが、作り直すのか使い続けるのか迷う（要確認）
- recommend: 直してもらう 7, 直さない 3

前回のあとに足した一文。0-2 は「古い版の作業用ディレクトリに project-code-base-report.html があれば、レポートはそのファイルにあるので、そこで使い続ける」と書く。step0 の README（L33）は「古い katachi が作った作業用ディレクトリがあれば、この skill のテンプレートから新しく作り、要るものだけ移す」と書く。katachi agent init が作ったディレクトリにも project-code-base-report.html があるので、同じディレクトリに 2 つの指示が当てはまる。使い続けると、0-3 の読み書き（check-list.html の id="report" を sed で読む、report. で始まる Target、{"checklist": …, "report": …} の回答）と 1-2 の #report-questions が古いファイルと合わない。要確認

- 破っている項目: S2・S4（前回から消えた project-code-base-report.html が手順に残っていないか。groups.md の「前回からの大きな変更」）。根拠: skills/katachi/references/setup/step0/README.md:33
- 直し方の案: どちらかにそろえる。たとえば 0-2 の一文を「そのときは step0 の README のとおり新しく作り、古い project-code-base-report.html の JSON を report のブロックへ移す」にする。使い続けさせるなら、古いファイルでの読み書きの違いを 0-3 に 1 行足す
- 種類: ルールに反する箇所

## [p3] recommendation を選択肢の記号（B. …）で始めることが、手順に書かれていない
- file: skills/katachi/references/setup/step0/0-3-checklist-and-report.md
- line: 41
- quote: `options` (array), `recommendation`
- impact: あり: 記号を付けずに書くと、ユーザのページに推奨の印と「推奨を採用」が出ない（要確認）
- recommend: 直してもらう 7, 直さない 3

ページは recommendation の先頭の文字が選択肢の記号（A・B…）のときだけ、その選択肢に「推奨」の印を付け、「推奨を採用」「未回答に推奨を採用」を使えるようにする（assets/ja/check-list.html:2610-2615・:2700）。記入例は "B。対象が1画面だけで…" と書くが、0-3 の表（L41・L42）と 1-1・1-2・4-2 は recommendation の形を言わない。記号なしで書くと印もボタンも出ず、形を知る手がかりは HTML のコメントの記入例だけ。手順 5・9（実装と手順書の一致）にも回す

- 破っている項目: S2 の趣旨（skill の手順と assets/{en,ja}/check-list.html が同じ形を前提にする。docs/AGENTS.md:238）。要確認
- 直し方の案: 0-3 の表の下に 1 行足す。「`recommendation` starts with the letter of the option you recommend (`B. …`; the page marks that option and offers "Adopt recommendation")」
- 種類: ルールに反する箇所

## [p3] 「katachi's own Gradle and Git groups」の Git group は katachi に無い（要確認）
- file: skills/katachi/references/setup/step3/3-2-write-the-definition.md
- line: 34
- quote: as katachi's own `Gradle` and `Git` groups are
- impact: あり: git() が gradle() と同じく用意されていると読める（コンパイルエラーから直せる。要確認）
- recommend: 直してもらう 7, 直さない 3

前回のあとに足した文。Gradle group は katachi が持つ（katachi/src/main/kotlin/me/tbsten/katachi/dsl/gradle/GradleGroup.kt:93）が、Git group は Git のレシピの見本にあるだけでライブラリには無い（サンプルの git() は "Git".role）。同じファイルの L46 は gradle() を katachi が宣言すると書くので、git() も同じように呼べると読める。実装との食い違いなので手順 5 にも回す。要確認

- 破っている項目: A5（group 名は大文字で始める。この行が出どころの 1 つ）・F8（katachi が持つのは gradle() の 1 行）。根拠: recipes/git.mdx:14-29（git() の中身は自分の git/GitGroup.kt に置く）
- 直し方の案: 「as katachi's own `Gradle` group and the `Git` group of the recipes are」
- 種類: ルールに反する箇所

## [p3] Gradle のレシピの "Build".group が、skill の「group に Build と付けない」と食い違う（要確認）
- file: skills/katachi/references/setup/step3/3-3-refactor.md
- line: 8
- quote: Do not name a group `Build` or `Out`: its directory becomes `build/` or `out/` (use e.g. `BuildLogic`)
- impact: あり: レシピどおりの group 名で定義を分けると、定義のファイルが .gitignore に一致して検査から外れる（要確認）
- recommend: 直してもらう 7, 直さない 3

3-1-fetch-docs.md:11（前回のあとに足した行）は buildSrc・includeBuild のあるプロジェクトで Gradle のレシピを読ませる。レシピは "Build".group に 4 つの Role を入れる形で、skill はこの group 名を名指しで禁じている（定義を分けると build/BuildGroup.kt になり、.gitignore の build/ に一致して検査から外れる）。エージェントは写したあと 3-3 で名前を変えることになり、レシピだけを読んだ人は気づけない。要確認

- 破っている項目: E（ガイドと skill の食い違い）。S3（skill の決まり: .gitignore に一致する group 名を付けない。add-and-update/add-a-role.md:30 も同じ）と recipes/gradle.mdx:34・:52（"Build".group）
- 直し方の案: レシピの group 名を "BuildLogic" に変える（template=BuildLogic.ConventionPluginScript）。レシピをそのままにするなら、3-1 の L11 に「レシピの Build group は BuildLogic に読み替える」と添える
- 種類: ガイド・ルールの側の食い違い（E）

## [p3] 「capture() に変えても検査の結果は変わらない」が、同じ名前を 2 か所に書く直後の例に当てはまらない（F2 とガイドの側の食い違い）
- file: skills/katachi/references/setup/step6/6-D-templates.md
- line: 28
- quote: The check's result doesn't change.
- impact: あり: 同じファイルの L28 と L53 が逆のことを言う。原因はガイドの側（要確認）
- recommend: 直してもらう 7, 直さない 3

チェックリストの F2 は「同じ値であることを検査する」という説明を NG とするが、実装は同じ名前を 1 つのパスに 2 回書くと値がそろっていることを検査する（katachi/src/main/kotlin/me/tbsten/katachi/dsl/LayoutScope.kt:136-138）。skill の 3 か所（6-D の L53、follow-a-move.md:15、fix-violations/README.md:33）は実装と合っている。ガイドの文は手順 5 が p4 で出している（docs-vs-impl.md:64）。skill の中では L28 のこの文だけがガイドと同じ言い方で、直後の例と L53 に合わない。

- 破っている項目: E（ルールの側の食い違い）。F2（capture() は検査の上では * と同じ。guides/generate-code-from-template.mdx:131）と、実装・skill の説明
- 直し方の案: ガイドを手順 5 の案どおり直すときに、この文も「A name written once does not change the check's result; the same name written twice also requires the two values to agree (below)」とそろえる。チェックリストの F2 の見分け方も直す
- 種類: ガイド・ルールの側の食い違い（E）

## [p2] 「invoke 関数を1つだけ持つ」と書くが、required は 1 つ以上を求めるだけ（要確認）
- file: ja/get-started/comparison-with-other-tools.mdx
- line: 77
- quote: <summary>`UseCase` は `invoke` 関数を1つだけ持つ</summary>
- impact: あり: invoke のオーバーロードも違反になると読める（要確認）
- recommend: 直してもらう 7, 直さない 5

required(suspend.optional(), operator, `fun`, "invoke")(anyArgs()) は invoke が 1 つ以上あることを求める行で、引数の違う invoke を 2 つ書いても違反にならない（katachi-konsist の FunctionRequired の KDoc）。この例が禁止するのは invoke 以外の public な宣言。L65 と motivation.mdx:307 のコメント「invoke 関数が必ず 1 つ」は「必ず 1 つはある」とも読めるので件数に入れていない。要確認。手順 5 にも回す。

- 破っている項目: C3（required(...) は必ず存在する宣言。数を 1 つに限る意味は無い）。根拠: guides/kt-file-content.mdx「class, interface, object 宣言を許可する」
- 直し方の案: <summary> を「`UseCase` の public な関数は `invoke` だけ」にする。
- 種類: ルールに反する箇所

## [p2] interface だけを入れる Role の名前が Repository（要確認）
- file: ja/get-started/comparison-with-other-tools.mdx
- line: 105
- quote: "Repository".role {
- impact: あり: 写すと、ガイドと違う Role 名（実装の Role と並べたときに区別の付かない名前）が広まる（要確認）
- recommend: 直してもらう 7, 直さない 5

*Repository の interface だけを入れる Role を "Repository".role と書いている。guides/role.mdx の 🆗 の例は、同じ中身（required(public, `interface`, fileName) { allow(suspend.optional(), `fun`, "*") }）の Role を RepositoryInterface と呼ぶ。1 Role に 2 種類は入れていない（B1 には反しない）ので、名前だけの食い違い。要確認。

- 破っている項目: A7（Repository の interface と実装の Role 名は RepositoryInterface と RepositoryImplementation）。根拠: docs/AGENTS.md「文章の方針」の 🆗 の例、guides/role.mdx「1つの Role には1種類のファイルだけを入れる」、guides/basic-api.mdx:85
- 直し方の案: Role 名を "RepositoryInterface" にし、<summary> を「`RepositoryInterface` のファイルは interface を宣言する」のように合わせる。
- 種類: ルールに反する箇所

## [p2] 「参照すると違反になる」が、import したときに限られることを言っていない（要確認）
- file: ja/get-started/comparison-with-other-tools.mdx
- line: 148
- quote: ViewModel から Android の View 型を参照すると違反になるので
- impact: あり: 完全修飾名での参照も検出されると読める
- recommend: 直してもらう 7, 解決済みにする 5, 直さない 3

前回のあとに書き換えた行。この例で違反になるのは android.view を import したときで、本体の中に完全修飾名で書いた参照は ktFileContent の対象外。次の文は「import した時点で違反になります」と正しく言っているので、1 文目だけが実際より広い。L146 の <summary>（「参照しない」）も同じ言い方。要確認。手順 5 にも回す。

- 破っている項目: C8（ktFileContent が見るのはファイルの形まで。宣言の本体の中身は対象外）。根拠: guides/kt-file-content/kotlin-mapping.mdx「ktFileContent では書けないもの」。C1（import は許可リスト）
- 直し方の案: 「ViewModel のファイルが Android の View 型を import すると違反になるので、層をまたぐ依存を検出できます。`import` の行に書いたものだけが許可されるので、`android.view` を並べなければ違反になります。」
- 種類: ルールに反する箇所

## [p2] 「手順 5」「手順 3」がどのタブの手順かを書いていない（要確認）
- file: ja/get-started/first-architecture.mdx
- line: 250
- quote: 手順 5 で出た違反を片付けたあとの例です。
- impact: あり: AI Agent のタブで導入した読み手が、無い手順を探す
- recommend: 直してもらう 7, 直さない 5

「手順 5」「手順 3」は、前のセクションの「手動でインストールする」タブの手順を指す。同じセクションの「AI Agent に任せる」タブは手順が 4 つで、手順 3 はレビュー。AI Agent のタブで導入した読み手には指す先が無い。要確認。

- 破っている項目: D3-4（指示語ではなく、指すものを書く。節を移すと指す先が変わる）。根拠: docs/AGENTS.md「文章の方針」
- 直し方の案: 「「手動でインストールする」の手順 5 で出た違反を片付けたあとの例です。手順 3 の定義では…」とタブの名前を添える。または「最初の実行で出た違反を片付けたあとの例です。`domain/src/main/kotlin/usecase/` に `*UseCase.kt` を置くと宣言した定義で、…」と指すものを書く。
- 種類: ルールに反する箇所

## [p2] リンクの文字がリンク先の title と違う（E-b・basic-api）（要確認）
- file: ja/guides/basic-api.mdx
- line: 244
- quote: [ファイルの中で定義できる宣言の許可リストを定義する](/katachi/ja/guides/kt-file-content/)
- impact: あり: リンクの文字と着いた先の題が違う。リンク先には着ける（要確認）
- recommend: 直してもらう 7, 解決済みにする 5, 直さない 4

リンクの文字が、リンク先のページの以前の題になっている。今の title は ktFileContent。要確認（説明の文としては読める）。

- 破っている項目: E-b（checklist.md）。リンク先 guides/kt-file-content.mdx の title は ktFileContent、サイドバーは「ktFileContent > 概要」。docs/AGENTS.md L83 の木が古い題のまま（E-a と同じ原因）
- 直し方の案: 「`ktFileContent { }` については [ktFileContent のガイド](/katachi/ja/guides/kt-file-content/) も参照してください。」にする。あわせて AGENTS の木 L83 を今の title に直す。
- 種類: ガイド・ルールの側の食い違い（E）

## [p2] processor への案内を削ったあとに、値の持ち方だけを言う 1 文が残った（要確認）
- file: ja/guides/document-generation.mdx
- line: 288
- quote: 値そのものは中の Role や group に写されません（それぞれ自分が書いた値を持ちます）。
- impact: あり: 何のための文かが分からず、「中身ごと外れる」という直前の説明と逆に読める（要確認）
- recommend: 直してもらう 7, 解決済みにする 5, 直さない 4

前回はこの文のあとに「自作の processor でこの値を見るときは、Role.groupPath をたどって…」が続き、processor から値を読む人への前置きだった。今回その案内を削ったので、残った文は documented の値が内部でどう持たれるかだけを言う。ドキュメントに出るかどうかは L279 で言い終えていて、見出しの「group の中身ごと外します」の直後に「写されません」と続くので逆のことを言っているようにも読める。<details> の中なので priority は低い。要確認。

- 破っている項目: D3-6（利用者が最初に知らなくてよい細部を書かない。内部の仕組み・同じことの言い換え）。docs/AGENTS.md「文章の方針」
- 直し方の案: L288 の 1 文を削る（要るなら documented の KDoc に任せる）
- 種類: ルールに反する箇所

## [p2] capture() の最初の見本に、名前の無い `*ViewModel` が残っている（要確認）
- file: ja/guides/generate-code-from-template.mdx
- line: 127
- quote: "feature" / capture("feature") / "src" / "main" / "kotlin" / "*ViewModel".ktFile()
- impact: あり: 写して .template { } を付けるとエラーになる。エラーから直せる（要確認）
- recommend: 直してもらう 7, 直さない 4

節「生成先にワイルドカードがあるときは、名前を付けて値を渡す」の最初の見本が、ディレクトリには capture("feature") を付け、ファイル名は "*ViewModel" のままにしている。この宣言に .template { } を付けると L135 のとおりエラーになり、付けないなら L133 のとおり capture() は要らない。要確認（capture() の書き方だけを見せる断片とも読める。このまま: E9 は kt-file-content.mdx の例の話で、生成先の `*` が残る点はこの箇所だけ）。

- 破っている項目: F2（テンプレートを持たない Role は `*` のままでよい。同じページの L133）・F1（.template { } の生成先に名前の無い `*` を残さない。同じページの L135）
- 直し方の案: `"feature" / capture("feature") / "src" / "main" / "kotlin" / "${capture("name")}ViewModel".ktFile()` にする。または断片の見本として直さない。
- 種類: ルールに反する箇所

## [p2] 「両方の `*`」が指す 2 か所を、文が言わなくなった
- file: ja/guides/generate-code-from-template.mdx
- line: 165
- quote: 両方の `*` が決まります
- impact: あり: 「両方の `*`」に対応する箇所を、読み手がコード例から探すことになる
- recommend: 直してもらう 7, 解決済みにする 5, 直さない 3

前回は直前に「モジュールのディレクトリとパッケージのディレクトリには、同じ名前 feature の capture を書いています」があったが、今回その文を削ったので「両方の `*`」が何と何かを言う文が無い。コード例（L140-142）に `*` は 1 つも書かれておらず、capture() は 3 つある（feature が 2 つと name）。

- 破っている項目: D3-4（指示語ではなく、指すものを書く）・D3-5（何と何の話かを、最初の一文で言う）。docs/AGENTS.md L331-335
- 直し方の案: 「`capture("feature")` を 2 か所に書いています。同じ名前の capture は同じ値になるので、`--arg feature=home` の 1 つで 2 か所とも `home` に決まります。」のように、指す 2 か所を書く。
- 種類: ルールに反する箇所

## [p2] 依存を足すコード例の title が build.gradle.kts だけで、どのモジュールか分からない（要確認）
- file: ja/guides/kt-file-content.mdx
- line: 98
- quote: title="build.gradle.kts"
- impact: あり: どのモジュールの build.gradle.kts に足すのかを読み手が決めることになる（要確認）
- recommend: 直してもらう 7, 解決済みにする 5, 直さない 4

katachi-konsist の依存を足す例だけ title が `build.gradle.kts` で、直前の本文（L96）もモジュールを言わない。ルートやアプリのモジュールに足すと `ktFileContent { }` が解決できない。要確認（「初めてのアーキテクチャ定義」の手順 2 が同じ依存を既に入れているので、迷う人は少ないかもしれない。バージョンの書き方も `<version>` と `<katachi のバージョン>` で違う）。

- 破っている項目: F9（導入の形をそろえる。モジュールは :architecture-test）。get-started/first-architecture.mdx L111・L152 と、ほかのガイドの 7 か所は title="architecture-test/build.gradle.kts"
- 直し方の案: title を `architecture-test/build.gradle.kts` にし、L96 を「`architecture-test/build.gradle.kts` の依存に追加してください。」にする
- 種類: ルールに反する箇所

## [p2] リンクの文字がリンク先の title と違う（E-b・role）（要確認）
- file: ja/guides/role.mdx
- line: 209
- quote: [ファイルの中で定義できる宣言の許可リストを定義する](/katachi/ja/guides/kt-file-content/)
- impact: あり: リンクの文字と着いた先の題が違う。リンク先には着ける（要確認）
- recommend: 直してもらう 7, 解決済みにする 5, 直さない 4

basic-api.mdx L244 と同じ。リンクの文字が、リンク先のページの以前の題になっている。要確認。

- 破っている項目: E-b（checklist.md）。リンク先 guides/kt-file-content.mdx の title は ktFileContent。docs/AGENTS.md L83 の木が古い題のまま
- 直し方の案: 「`ktFileContent { }` の書き方は、[ktFileContent のガイド](/katachi/ja/guides/kt-file-content/) で詳しく説明しています。」にする。
- 種類: ガイド・ルールの側の食い違い（E）

## [p2] 「使うツールの分だけ呼びます」が、何を呼ぶのかを書いていない（要確認）
- file: ja/recipes/ai-agent.mdx
- line: 16
- quote: 使うツールの分だけ呼びます。
- impact: あり: 何を呼ぶのかが、次のセクションを読むまで分からない（要確認）
- recommend: 解決済みにする 7, 直さない 5

呼ぶもの（claudeCode()・cursor() などツールごとの関数）は、次のセクション（L20）で初めて出てくる。冒頭の 2 文だけを読むと、何を呼ぶのかが分からない。前回から変わっていない行。要確認

- 破っている項目: D3-5（何と何の話かを、最初の一文で言う）・D3-4（指すものを書く）。根拠は docs/AGENTS.md「文章の方針」
- 直し方の案: 「`claudeCode()`・`cursor()` のようなツールごとの関数を、使うツールの分だけ呼びます。」
- 種類: ルールに反する箇所

## [p2] コメントは「Role を並べます」だが、並べるのは 2 つの group（要確認）
- file: ja/recipes/github.mdx
- line: 41
- quote: // 次のセクションの Role を、この中に並べます
- impact: なし: 次のセクションのコードが group ごと載っているので、写した結果は変わらない
- recommend: 解決済みにする 7, 直さない 5

次の 2 つのセクションのコードは "Actions".group { … } と "Templates".group { … } で、"GitHub".group の中に並べるのは group。L29 の本文は「中は Actions と Templates の2つに分かれます」と書いている。gradle のレシピの同じ位置のコメント（L55）は中身と合っている。要確認

- 破っている項目: A1（katachi の概念は用語で書く。group と Role を呼び替えない）。根拠は docs/AGENTS.md「文章の方針」
- 直し方の案: 「// 次の2つのセクションの group（Actions と Templates）を、この中に並べます」
- 種類: ルールに反する箇所

## [p2] フィードバックの見本の group が小文字（app › ui）で、同じファイルの Target（App.Ui）と合わない
- file: skills/katachi/references/setup/step0/0-3-checklist-and-report.md
- line: 83
- quote: app › ui › ViewModel
- impact: なし: 見本の表示名だけで、エージェントは Target のキーで直す
- recommend: 直してもらう 8, 直さない 2

前回のあとに、同じファイルの L97 は report.roles[group=App.Ui]、1-2 は Feature.Ui、記入例は "group": "App.Ui" に直った。見本の L83 だけ app › ui › ViewModel のまま残った。ページは group の名前をそのまま表示名にするので（assets/en/check-list.html:4499）、App.Ui なら App › Ui › ViewModel と出る。「このまま: E13」（AGENTS の決まりを根拠に skill の小文字 group を指摘しない）とは根拠が違い、skill 自身の決まりと同じファイルの中の不一致

- 破っている項目: S3（skill が自分で述べる決まり: group 名は UpperCamelCase。skills/katachi/references/setup/step1/1-2-fill-the-report.md:21・step3/3-2-write-the-definition.md:34）・A5（出力の写しも group 名を大文字で始める）
- 直し方の案: 「1. Codebase report › 2. Elements (candidate roles) › App › Ui › ViewModel」にする
- 種類: ルールに反する箇所

## [p2] フィードバックの見本の節の名前「1. Overview」が、今のページ（1. Architecture findings）と合わない
- file: skills/katachi/references/setup/step0/0-3-checklist-and-report.md
- line: 87
- quote: 1. Overview › Main frameworks and 1 more
- impact: なし: 見本の表示名だけで、Target のキー（report.overview.frameworks）は合っている
- recommend: 直してもらう 8, 直さない 2

レポートを check-list.html に入れたときに節が 7 つから 5 つになり、1 つ目の節は「1. Architecture findings」（ja は「1. アーキテクチャ調査結果」）になった（assets/en/check-list.html:1956・:1965）。見本は L83 の「4. Elements」を「2. Elements」に直したが、L87〜89 の 3 行は「1. Overview」のまま。実際の貼り付けは Codebase report › 1. Architecture findings › Main frameworks と出る

- 破っている項目: S2 の趣旨（skill の手順と check-list.html の対応）。要確認。手順 5（実装と手順書の一致）寄り
- 直し方の案: L87〜89 の「1. Overview」を「1. Architecture findings」にする
- 種類: ルールに反する箇所

## [p2] 6-0 に、2 ファイルだった頃の「the id decides the file」が残っている
- file: skills/katachi/references/setup/step6/6-0-fix-after-review.md
- line: 7
- quote: the id decides the file: `cq…` the report, `q…` the checklist
- impact: あり: 無くなった 2 つ目のファイルを探させる言い方が残っている
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

同じファイルの L5 は今回 both files を both blocks に直したが、L7 が残った。ファイルは 1 つなので、無くなったレポートのファイルを探させる言い方になる。

- 破っている項目: S2 の趣旨（skill の手順と check-list.html の対応。レポートは check-list.html の id="report" のブロックに入った）。根拠: skills/katachi/references/setup/step0/0-3-checklist-and-report.md:3・:59
- 直し方の案: 「the id decides the block」
- 種類: ルールに反する箇所

## [p2] step 6 の報告の説明に、2 ファイルだった頃の「that file」が残っている
- file: skills/katachi/references/setup/step6/README.md
- line: 85
- quote: Leave out a "Questions" line when that file has no questions.
- impact: あり: チェックリストとレポートが別のファイルだと読める
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

報告の雛形の 2 行（check list の質問・report の質問）は、今はどちらも check-list.html の中のブロックを指す。前回から変えていない行で、同じファイルの L62・L87 は今回 1 ファイルの言い方に直っている。

- 破っている項目: S2 の趣旨（skill の手順と check-list.html の対応）。根拠: skills/katachi/references/setup/step0/0-3-checklist-and-report.md:3（1 つの HTML に 2 つのブロック）
- 直し方の案: 「when that block has no questions」
- 種類: ルールに反する箇所

## [p1] AGENTS の木が Layout のページタイトルを古い題で書いている（E-a）
- file: docs/AGENTS.md
- line: 81
- quote: ページタイトルは「Layout システムでディレクトリ構成を厳守させる」
- impact: なし: 書き手向けの決まり
- recommend: 直してもらう 7, 直さない 3

guides/layout.mdx の frontmatter は title: Layout（sidebar.label も Layout）で、木の注記の題と違う。

- 破っている項目: E-a（checklist.md。AGENTS の木が現物とずれている）。checklist.md の E-a に挙がっていない箇所
- 直し方の案: 木の L81 の注記を消す（`- Layout` だけにする）。docs/CLAUDE.md は同じ内容なので一緒に直る。
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] AGENTS の木が、ktFileContent の概要ページを古い題で書いている（E-a）
- file: docs/AGENTS.md
- line: 83
- quote: ファイルの中で定義できる宣言の許可リストを定義する
- impact: なし: 書き手向けの決まり
- recommend: 直してもらう 7, 直さない 3

木の L83 は以前の題のまま。ほかのページのリンクの文字（E-b の 3 か所）が古い題のままなのも同じ原因。

- 破っている項目: E-a（AGENTS の木が現物とずれている）。guides/kt-file-content.mdx の frontmatter は title: "ktFileContent"（L2）、サイドバーは「ktFileContent > 概要」
- 直し方の案: 木の L83 を「- ktFileContent   ［サイドバーの表示は「概要」］（…）」にする。docs/CLAUDE.md は同じ内容なので一緒に直る
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] AGENTS の木に、マッピングのページのサイドバーの表示名「言語機能との対応」が無い（E-a）
- file: docs/AGENTS.md
- line: 84
- quote: Kotlin 言語機能とのマッピング
- impact: なし: 書き手向けの決まり
- recommend: 直してもらう 7, 直さない 3

ページの title（Kotlin 言語機能とのマッピング）は木と合うが、サイドバーの表示は「言語機能との対応」。木のほかの行は、表示名と title が違うときに「［サイドバーの表示は…］」「［ページタイトルは…］」を添えている。

- 破っている項目: E-a（AGENTS の木が現物とずれている）。docs/astro.config.mjs L49 のサイドバーの表示は「言語機能との対応」
- 直し方の案: 木の L84 に「［サイドバーの表示は「言語機能との対応」］」を添える
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] AGENTS の木の「差分レポート」の注記が現物と合わない（E-a）
- file: docs/AGENTS.md
- line: 88
- quote: PR にコメントする使い方は GitHub のレシピ
- impact: なし: 書き手向けの決まり
- recommend: 直してもらう 7, 直さない 3

木は「[AI生成] 差分レポート（katachiDiff。PR にコメントする使い方は GitHub のレシピ、processor の表は ArchitectureProcessor）」と書く。現物はサイドバーの表示が「Diff report」、title が「Diff report で変更の影響範囲を確認する」。PR にコメントする action の使い方は guides/diff-report.mdx の「CI に組み込む」（L217-265）にあり、recipes/github.mdx には無い。processor の表（guides/processor.mdx:105-107）は合っている。

- 破っている項目: E-a（checklist.md。docs/AGENTS.md の木が現物とずれている）。表示名のずれは checklist.md の E-a に挙がっている。注記の中身のずれは挙がっていない箇所
- 直し方の案: 木の L88 を「Diff report（`katachiDiff`。PR にコメントする action の使い方もこのページ。processor の表は ArchitectureProcessor）」にする。docs/CLAUDE.md は AGENTS.md へのリンクなので一緒に直る
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] AGENTS の木は「CLI と script」の概要に「全体の流れ」があると書くが、ページに無い（E-a・要確認）
- file: docs/AGENTS.md
- line: 90
- quote: （ユースケース・インストール・全体の流れ）
- impact: なし: 書き手向けの決まり
- recommend: 直してもらう 7, 直さない 4

木は概要のページの中身を「ユースケース・インストール・全体の流れ」とする。guides/cli-and-script.mdx にあるのは冒頭の 2 文・「ユースケース」・「インストール」・「次のステップ」で、全体の流れを説明するセクションは無い。木を直すのか、ページに足すのかはルールの側の判断。要確認。

- 破っている項目: E-a（checklist.md。docs/AGENTS.md の木が現物とずれている）。checklist.md の E-a に挙がっていない箇所。D1-14（ページの中身が、木に書かれた位置づけと合う）
- 直し方の案: 木の L90 の注記を「（ユースケース・インストール）」にする
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] 比較表は Konture を「統合あり」と書くが、使い方のページが docs に無い（E-a・要確認）
- file: ja/get-started/comparison-with-other-tools.mdx
- line: 29
- quote: 統合あり（`katachi-konture`）
- impact: あり: 「統合あり」の使い方を docs で探せない（要確認）
- recommend: 直さない 7, 直してもらう 5

比較表は Konture を「🆗 統合あり（katachi-konture）」と書く。使い方のページ（guides/konture-integration）は 2d43371f で外され、今の ja のページで Konture に触れるのはこのページだけ。モジュール katachi-konture は settings.gradle.kts にある。AGENTS の木（L85）にはページが残ったまま。担当の get-started 4 ページと roadmap は、木とサイドバー（docs/astro.config.mjs）で一致している。showcase.mdx（draft: true）は木にもサイドバーにも無い。要確認。

- 破っている項目: E-a（docs/AGENTS.md の木 L85「Konture との統合」のページが 2d43371f で消えていて、サイドバーにも無い）
- 直し方の案: AGENTS の木から L85 を消す。表は「統合あり」のまま API リファレンスへのリンクを付けるか、案内先ができるまで「🆗 可能」にするかを利用者が決める。
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] ハイライト {7-12} が閉じかっこ（11・12 行目）を含む（要確認）
- file: ja/guides/basic-api.mdx
- line: 201
- quote: kt {7-12} nocollapse
- impact: なし: ハイライトの範囲だけで、意味は変わらない
- recommend: 直さない 8, 直してもらう 3

ブロックの 11 行目は required(...) { を閉じる }、12 行目は ktFileContent { を閉じる }。ktFileContent { } のブロック全体を指す意図ならこのままでよい（kt-file-content.mdx の同じ形は過去に「直さない」）。今回 layout.mdx:24 は {4-6} → {4-5} に直っているので、そろえるかの確認。要確認。

- 破っている項目: D2-5（ハイライトは読んでほしい行を指す。空行・閉じかっこは指さない）。docs/AGENTS.md L345-346
- 直し方の案: {7-10} にする。またはブロック全体を指す意図として直さない。
- 種類: ルールに反する箇所

## [p1] 言い換えのあと「Markdownを」の半角スペースが落ちた
- file: ja/guides/document-generation.mdx
- line: 292
- quote: そこから生成される Markdownを並べて示します。
- impact: なし: 表記ゆれ
- recommend: 解決済みにする 7, 直してもらう 5, 直さない 3

今回「マークダウン」→「Markdown」に言い換えた行。同じページのほかの箇所（L15「Markdown ドキュメント」・L217「Markdown が」・パネルの題「生成される Markdown」）は英字と和文の間を空けているが、ここは「Markdownを」と続いている。L529 にも同じ形がある（g4-03 に含めた）。

- 破っている項目: 表記ゆれ（priority の基準表の 1〜2）。チェックリストに項目は無い。前回から変わった行の確認で見つけた
- 直し方の案: 「次の group 定義と、そこから生成される Markdown を並べて示します。」
- 種類: ルールに反する箇所

## [p1] 段落の最初の文に主語が無い「検査の上では `*` とまったく同じです。」（要確認）
- file: ja/guides/generate-code-from-template.mdx
- line: 131
- quote: 検査の上では `*` とまったく同じです。
- impact: なし: 次の文で意味が分かる。言い回しだけ
- recommend: 直さない 6, 解決済みにする 5

コード例の直後の段落が、何が `*` と同じかを言わずに始まる。次の文に capture("feature") が出るので意味は取れる。要確認（今回さわった行。空白を削っただけ）。

- 破っている項目: D3-5（何と何の話かを、最初の一文で言う）。docs/AGENTS.md L334-335 の 🆖「見ている範囲が違います。」と同じ形
- 直し方の案: 「`capture()` は、検査の上では `*` とまったく同じです。」と主語を足す。
- 種類: ルールに反する箇所

## [p1] 節の最初の文の「そのファイル内に」が指すファイルが、前の文に無い（要確認）
- file: ja/guides/kt-file-content.mdx
- line: 138
- quote: そのファイル内に class, interface, object の配置を許可する場合
- impact: なし: 意味は取れる。言い回しだけ
- recommend: 直さない 7, 解決済みにする 4

「class, interface, object 宣言を許可する」の最初の文が「そのファイル内に」で始まる。直前の文（L134）の「ファイル」は例を写す先のファイルの話で、指しているのは Role の Kotlin ファイル。今回この文の後半だけを書き換えた。要確認（意味は取れる）。

- 破っている項目: D3-4（指示語ではなく、指すものを書く）。docs/AGENTS.md L331-333
- 直し方の案: 「Role の Kotlin ファイルに class, interface, object を置けるようにするには、`required(...)` または `allow(...)` に、許可したい宣言の種類と名前を渡します。」
- 種類: ルールに反する箇所

## [p1] 「layout { } で縛れます」は比喩（要確認）
- file: ja/guides/kt-file-content/kotlin-mapping.mdx
- line: 701
- quote: 置き場所は `layout { }` で縛れます
- impact: なし: 意味は取れる。言い回しだけ
- recommend: 直さない 7, 解決済みにする 5

「ktFileContent では書けないもの」の表の `.kts` の行。「縛る」は日本語の docs 全体でここ 1 か所だけ。要確認（意味は取れる）。

- 破っている項目: D3-9（挙動を比喩で言わず、何が起きるかを書く）・D3-15（比喩的な動詞）。docs/AGENTS.md L352-356
- 直し方の案: 「置き場所は `layout { }` で宣言できます」
- 種類: ルールに反する箇所

## [p1] ignore() の見本が build で、デフォルトの gitTracked() では要らない宣言になる（E-c・見本）（要確認）
- file: ja/guides/layout.mdx
- line: 101
- quote: "build".ignore()
- impact: なし: ルールの側の食い違い。写しても検査の結果は変わらない
- recommend: 直さない 8, 直してもらう 2

build/ はたいてい .gitignore にあるので、デフォルトの gitTracked() のままなら宣言は要らない。見本（L97-103）はその前提を言わない。要確認。

- 破っている項目: E-c（checklist.md）。同じページの L146（wholeTree() のときに build/ を ignore() で外す）・L150（gitTracked() では .gitignore に書いたものに Role は要らない）。docs/AGENTS.md L343-344 も "build".ignore() を 🆗 の見本にしている
- 直し方の案: 直さない（ignore() の書き方の見本として分かりやすい）。直すなら L97 に「files = wholeTree() のとき」と一言足すか、見本を git が追跡する生成物のディレクトリに変える。
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] 「ここにあれば何でも」の Role を書かない決まりが、Layout のガイドに無い（E-e）
- file: ja/guides/layout.mdx
- line: 118
- quote: katachi 以外のツールが構成を決めているもの
- impact: なし: ルールの側の話
- recommend: 直さない 8, 直してもらう 2

Layout のガイドの caution（L114-119）は anyFile() と ignore() だけを逃げ道として挙げ、"**" / "*".ktFile() のような layout には触れていない。docs への同じ趣旨の指摘は、レシピで全部「直さない」になっている。

- 破っている項目: E-e（checklist.md。B13・S3）。決まりは skills/katachi/references/setup/step3/3-2-write-the-definition.md L87-90 だけにある
- 直し方の案: 直さない。書くなら caution に 1 文足す。
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] ignore() の見本が build で、デフォルトの gitTracked() では要らない宣言になる（E-c・まとめ）（要確認）
- file: ja/guides/layout.mdx
- line: 193
- quote: // それより下は検査しない
- impact: なし: ルールの側の食い違い。写しても検査の結果は変わらない
- recommend: 直さない 8, 直してもらう 2

L101 と同じ食い違いが、まとめの完成形にもある。デフォルトの gitTracked() のままなら、BuildOutput の Role は要らない。要確認。

- 破っている項目: E-c（checklist.md）。まとめの完成形の "BuildOutput".role { layout { "build".ignore() } }。同じページの L146・L150
- 直し方の案: 直さない。直すなら L101 と同じ（前提を一言足すか、見本のディレクトリを変える）。
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] リンクの文字「テンプレートからのコード生成」がリンク先の title と違う（GenerateCodeFromTemplate の行）
- file: ja/guides/processor.mdx
- line: 97
- quote: テンプレートからのコード生成
- impact: なし: 表記ゆれ。リンク先には着ける
- recommend: 直してもらう 7, 解決済みにする 6, 直さない 3

表の「ドキュメント」の列のリンクの文字が「テンプレートからのコード生成」。リンク先の title・サイドバーと、同じページの「次のステップ」のカード（L379）は「テンプレートからコード生成」。

- 破っている項目: 表記ゆれ（priority の基準表の 1〜2）。E-b と同じ種類（リンクの文字とリンク先の title）。リンク先 guides/generate-code-from-template.mdx の title とサイドバーは「テンプレートからコード生成」
- 直し方の案: 「テンプレートからコード生成」にする
- 種類: ルールに反する箇所

## [p1] リンクの文字「テンプレートからのコード生成」がリンク先の title と違う（DescribeTemplates の行）
- file: ja/guides/processor.mdx
- line: 102
- quote: テンプレートからのコード生成
- impact: なし: 表記ゆれ。リンク先には着ける
- recommend: 直してもらう 7, 解決済みにする 6, 直さない 3

g3-04 と同じ文字が DescribeTemplates の行にもある。

- 破っている項目: 表記ゆれ（priority の基準表の 1〜2）。E-b と同じ種類。g3-04 と同じ
- 直し方の案: 「テンプレートからコード生成」にする
- 種類: ルールに反する箇所

## [p1] カードの説明が比喩「同じ仕組みに乗っている」（要確認）
- file: ja/guides/processor.mdx
- line: 376
- quote: 同じ仕組みに乗っている、いちばん大きなビルトイン processor です。
- impact: なし: 意味は取れる。言い回しだけ
- recommend: 直さない 7, 解決済みにする 5

「ドキュメント生成」のカードの説明。「仕組みに乗っている」が比喩で、「いちばん大きな」も何が大きいのかを言わない。要確認（カードは読み流す場所。カードが processor を勧めること自体は方針で出さないので、ここは言い回しだけ）。

- 破っている項目: D3-9（挙動を比喩で言わず、何が起きるかを書く）・D3-15（比喩的な動詞）。LinkCard の description は textlint が見ない（D4-1 の範囲）
- 直し方の案: 「アーキテクチャ定義からドキュメントを生成する、ビルトインの processor です。」
- 種類: ルールに反する箇所

## [p1] 定義のファイルの名前（<Role>Role.kt）は、ガイドでは例に出るだけ（E-d）
- file: ja/guides/role.mdx
- line: 373
- quote: - UseCaseRole.kt
- impact: なし: ルールの側の話。ガイドの例は skill の決まりに合っている
- recommend: 直さない 8, 直してもらう 2

Role のガイドは domain/UseCaseRole.kt・domain/RepositoryInterfaceRole.kt（L370-375・L489-493）を例に出すが、名前の決まりは書いていない。ガイドに書くか、skill だけの決まりとするかは未決。

- 破っている項目: E-d（checklist.md。B14）。決まりとして書いてあるのは skills/katachi/references/setup/step3/3-3-refactor.md L22-36 だけ
- 直し方の案: 直さない（skill だけの決まりのまま）。ガイドに書くなら「Role 定義を分割する」に 1 文足す。
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] 「以下は」が指すものを書いておらず、「は」のあとに半角スペースがある（要確認）
- file: ja/recipes/android-three-layer.mdx
- line: 26
- quote: 以下は ドメイン層を採用する場合のアーキテクチャ定義の例です。
- impact: なし: 表記ゆれと、意味の変わらない指示語
- recommend: 解決済みにする 7, 直さない 6

「以下」の直後にあるのは定義ではなく図 1（group と Role の図）で、定義は 2 つ先のセクション（L71）にある。L16 が同じことをすでに言っている。「以下は」のあとの半角スペースは表記ゆれ。前回から変わっていない行。要確認

- 破っている項目: D3-4（指示語ではなく、指すものを書く）。根拠は docs/AGENTS.md「文章の方針」
- 直し方の案: 「図 1 は、ドメイン層を採用する場合の group と Role です。」とするか、L16 に「ドメイン層を採用する場合の」を足してこの文を消す
- 種類: ルールに反する箇所

## [p1] 地の文と LinkCard の説明が同じことを書いている（要確認）
- file: ja/recipes/detekt.mdx
- line: 21
- quote: description="Kotlin の静的解析ツール。ルールの有効・無効は YAML の設定ファイルに書く。"
- impact: なし: 意味の変わらない重複
- recommend: 直さない 7, 解決済みにする 5

L17「detekt では、ルールの有効・無効やしきい値を YAML の設定ファイルで設定できます。」の直後のカードが、同じ内容を繰り返す。対になる ktlint のレシピは、地の文の中のリンク 1 つで済ませている。前回から変わっていない行。要確認

- 破っている項目: D3-6（同じことの言い換えや重複した補足は書かない）。根拠は docs/AGENTS.md「文章の方針」
- 直し方の案: カードの説明を「Kotlin の静的解析ツール。」だけにする。または ktlint と同じく、カードをやめて L17 の「detekt」をリンクにする
- 種類: ルールに反する箇所

## [p1] 定義のファイルの置き方が、レシピの間で 2 通りになった（E-d）
- file: ja/recipes/git.mdx
- line: 29
- quote: `git()` の中身は `git/GitGroup.kt` に置きます。
- impact: なし: ルールの側が決まっていない（ファイル名が違っても定義は同じに動く）
- recommend: 直さない 7, 直してもらう 3

前回の Review の回答どおりに、git（git/GitGroup.kt）・github（github/GitHubGroup.kt）・ktlint（ktlint/KtlintGroup.kt）の 3 ページは <group>/<Group>Group.kt の形になった。ai-agent（roles/ClaudeCode.kt など 9 つ）・android-three-layer（roles/UiState.kt など 8 つと L365・L376）・detekt（roles/Detekt.kt）は「直さない」のまま（判断 18 件）で、レシピの中で置き方が 3 ページずつに分かれ、対になる ktlint と detekt でも違う。ガイド（guides/role.mdx:370-375）は例に出すだけで決まりとして書いていない。個々の箇所は判断済みなので出していない

- 破っている項目: E-d（定義のファイルの名前 B14 は、ガイドでは例に出るだけで、決まりは skills/katachi/references/setup/step3/3-3-refactor.md:22-36 にしか無い）
- 直し方の案: どちらかに決める。(a) skill だけの決まりとして、レシピの roles/*.kt はこのまま（今の判断どおり）。(b) Role のガイドの「Role 定義を分割する」に置き方を 1 文で書き、残りのレシピもそろえる
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] 助詞の誤り「katachi には…未熟であり」と、文中の半角スペース（要確認）
- file: ja/roadmap.mdx
- line: 16
- quote: katachi にはまだその点で非常に未熟であり
- impact: なし: 意味は通じる（表記の誤り）
- recommend: 直してもらう 8, 解決済みにする 5

「katachi には…未熟であり」は助詞が合っていない（「katachi は」）。同じ行の前半「使われ フィードバックを受けることで」に文中の半角スペースもある。前回のあとに L14 だけが書き直されていて、この行は前のまま。

- 破っている項目: D3-15（yomiyasu の観点で推敲する。docs/AGENTS.md:302-304）。助詞の誤りそのものはチェックリストに項目が無いので要確認
- 直し方の案: 「ツールは、実際のプロジェクトで使われてフィードバックを受けることで、実用性のあるツールに成長します。katachi はまだその点で未熟で、実際のプロジェクトで使ったユーザからのたくさんのフィードバックを必要としています。」
- 種類: ルールに反する箇所

## [p1] 誤字「このサイトに乗る」（要確認）
- file: ja/roadmap.mdx
- line: 20
- quote: あなたのプロジェクトがこのサイトに乗るチャンスです
- impact: なし: 意味は通じる（表記の誤り）
- recommend: 直してもらう 8, 解決済みにする 5

「乗る」は「載る」の誤字。載る先のショーケースのページ（showcase.mdx）は draft: true で、まだ公開されていない。

- 破っている項目: D3-15（yomiyasu の観点で推敲する。docs/AGENTS.md:302-304）。誤字そのものはチェックリストに項目が無いので要確認
- 直し方の案: 「あなたのプロジェクトがこのサイトに載るチャンスです！」
- 種類: ルールに反する箇所

## [p1] E-e: 「ここにあれば何でも」の Role を書かない決まりが、skill にしか無い
- file: skills/katachi/SKILL.md
- line: 31
- quote: No catch-all Role
- impact: なし: ルールの側の食い違い
- recommend: 直さない 8, 直してもらう 2

SKILL.md の「Common rules」は anyFile() と並べて "**" / "*.kt".file() だけの Role を catch-all として禁じる。ガイドはその形の layout を逃げ道として扱っていない。docs のレシピの同じ形への指摘は 2 件とも「直さない」になっている（android-three-layer の DomainModel、gradle の ConventionPluginSource）。決まりの主な出どころは 3-2（6b-skill の担当）

- 破っている項目: E-e（checklist.md の候補）。B13・S3（catch-all の Role を書かない。skills/katachi/references/setup/step3/3-2-write-the-definition.md:87-90）と B3（guides/layout.mdx:114-119 は anyFile()・ignore() だけを逃げ道と呼ぶ）
- 直し方の案: ルールの側の判断。ガイドの layout のページの caution に 1 文足すか、skill だけの決まりとしてこのままにする
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] E-d: 定義のファイルの名前の決まり（<group>/<Role>Role.kt）が、skill にしか無い
- file: skills/katachi/SKILL.md
- line: 44
- quote: <group>/<Role>Role.kt
- impact: なし: ルールの側の食い違い
- recommend: 直さない 8, 直してもらう 2

SKILL.md の木は「naming: setup 3-3」と決まりとして指す。ガイドは domain/UseCaseRole.kt を例に出すだけで、決まりとしては書いていない。docs のレシピの roles/*.kt への指摘は 9 件とも「直さない」になっている。決まりの主な出どころは 3-3（6b-skill の担当）

- 破っている項目: E-d（checklist.md の候補）。B14（guides/role.mdx:370-375・:489-493 は例に出すだけ。決まりは skills/katachi/references/setup/step3/3-3-refactor.md:22-36）
- 直し方の案: ルールの側の判断。ガイドに決まりとして書くか、skill だけの決まりとしてこのままにする
- 種類: ガイド・ルールの側の食い違い（E）

## [p1] 「ここにあれば何でも」の Role を書かない決まりが、skill にしか無い（E-e）
- file: skills/katachi/references/setup/step3/3-2-write-the-definition.md
- line: 87
- quote: **Do not write catch-all Roles.**
- impact: なし: ルールの側の食い違い
- recommend: 直さない 7, 直してもらう 3

担当のレシピには、ファイル名のパターンも ktFileContent も無い layout が 13 か所ある（ai-agent の **/* 11、android-three-layer の DomainModel、gradle の ConventionPluginSource）。どれも前回「直さない」になった。ガイドが逃げ道と呼ぶのは anyFile() と ignore() だけなので docs の決まりの上では違反にならないが、skill はエージェントに「書かない」と指示するので、skill が読ませるレシピと skill の決まりが食い違ったまま。レシピの側は判断済みなので出していない

- 破っている項目: E-e（B13・S3 の決まりは skill にだけある。ガイドの B3（anyFile()・ignore() は逃げ道、guides/layout.mdx:114-119）は "**" / "*".ktFile() のような layout に触れていない）
- 直し方の案: (a) skill だけの決まりとして、このまま。(b) Layout のガイドの「任意のファイルの配置を許す」に、"**" / "*".ktFile() のような layout も同じく逃げ道だと 1 文足す
- 種類: ガイド・ルールの側の食い違い（E）
