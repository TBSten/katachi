---
title: v0.4.0 リリース前チェック 3 回目（261112-01）手順 4・5・6
base: 5c1b0e37
---

## [p6] 出力先を変える例のとおりにすると、次のテストが生成ページの数だけ違反になる（出力先の決まりが書かれていない）
- file: ja/guides/document-generation.mdx
- line: 163
- quote: 出力先は、build.gradle.kts で `katachi.processors.docs.outputDir` を設定すると変更できます。
- impact: あり: 例どおり docs/architecture に出すと次の検査が失敗し、手書きの Markdown がある場所を指すと消える
- recommend: 直してもらう 9, 直さない 1

「出力先ディレクトリ」の節は、outputDir で出力先を変えられる、までしか書いていない。実装には決まりが 3 つある。(1) 出力先の中の、今回生成しなかった `*.md` は削除される（katachi/src/main/kotlin/me/tbsten/katachi/docs/internal/DocumentFiles.kt:9-16）。(2) build/ の外に出すと生成ページが gitTracked() の対象になり、Role を宣言しないと `[UnexpectedFile]` になる。(3) `--arg mode=check` は書かずに比べ、古いページや余分な `*.md` があると失敗する。動かして確かめた: ページの例のとおり docs/architecture に出すと、次の `:architecture-test:test` が生成ページの数だけ `[UnexpectedFile]` で失敗し、出力先に置いた手書きの `*.md` は `Removed …` のログとともに消えた。この 3 つは skill の 6-C と 6-B にだけ書いてある。英語のページも同じ。

- 直し方の案: 節に 3 点を足す。(1) 出力先のディレクトリは katachi のものになり、生成しなかった `*.md` は消える。(2) コミットするなら生成ページの Role を宣言する。(3) CI では `--arg mode=check` で古さを検出できる
- 出どころ: 手順 5 の 4（動かして確定。priority 4 → 6）。結果は /home/claude/katachi/.local/release-v0.4.0/tmp/docs-vs-impl/run-results.md の 5

## [p4] 横長の Mermaid の図が縮んで、Role 名が読めない（3 層の group の図）
- file: ja/recipes/android-three-layer.mdx
- line: 29
- quote: flowchart LR
- impact: あり: 図の Role 名が幅 1280 でも判読しづらく、幅 390 では読めない
- recommend: 直してもらう 7, 直さない 3

図 1（3 つの subgraph を横に並べた flowchart LR）は、viewBox の幅 1811px を本文の幅に縮めて出すので、字が幅 1280 で約 5.4px、幅 390 で約 2.7px になる。横スクロールにならず、押して拡大する手段も無い。直後のディレクトリツリーと Tabs に同じ Role 名が出るので、情報が失われるわけではない。英語のページも同じ（recipes/android-three-layer.mdx L28-29）。

- 同じ指摘: ja/guides/baseline.mdx L21
- 直し方の案: 図を縦にして横幅を抑える（flowchart TB にする、または subgraph を縦に積む）。または docs/src/styles の CSS で Mermaid の SVG を縮めず、図の枠の中で横スクロールさせる
- 出どころ: 手順 6 の en-01（priority 4）・ja-03（priority 3）
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/problem-android-mermaid-1280.png

## [p4] 「capture() を * に書き換えても変わらない」は、同じ名前を 2 か所に書くと成り立たない
- file: ja/guides/generate-code-from-template.mdx
- line: 131
- quote: `capture("feature")` を `"*"` に書き換えても、許されるファイルは1つも変わりません。
- impact: あり: `*` に名前を付けたら新しく違反になったとき、docs から理由にたどり着けない
- recommend: 直してもらう 8, 直さない 2

同じ名前の capture を 1 つのパスの 2 か所に書くと、検査は 2 か所の値が同じであることも求め、食い違うファイルは `[CaptureMismatch]` になる（katachi/src/main/kotlin/me/tbsten/katachi/check/CaptureViolations.kt）。直後の FeatureComponent の例（L140-141）は `capture("feature")` を 2 か所に書いているので、この文はすぐ下の例に当てはまらない。写しで動かして確かめた（2 か所の値が違うファイルは `[CaptureMismatch]`、2 つとも `"*"` にすると違反なし）。`[CaptureMismatch]` は日本語の docs に 1 度も出てこない。英語のページも同じ。

- 関連: 同じ行の言い回し（主語が無い）、L165 の「両方の `*`」、skill 6-D の同じ文への指摘は、手順 11 のファイルにある（どれも別の指摘）。layout.mdx L210 のカードの「Layout API の検査だけなら名前は要らず」は誤りではないので登録していない
- 直し方の案: 「1 か所に書くだけなら `*` と同じです」に狭め、「同じ名前を 2 か所に書くと、2 か所の値がそろっていることも検査されます。違ってよいなら別の名前にします」を 1〜2 文足す
- 出どころ: 手順 5 の 3（動かして確定）

## [p4] katachi lint が CLI のページに出てこない（コマンドリスト）
- file: ja/guides/cli-and-script/cli.mdx
- line: 29
- quote: [`agent`](#agent)
- impact: あり: リリースノートが lint の See 先にしているページに、lint が出てこない
- recommend: 直してもらう 8, 直さない 2

コマンドリストは doctor・scaffold・agent の 3 つで、lint が無い。`katachi --help` は doctor・scaffold・lint・`<ファイル>.katachi.kts` を並べ、`katachi agent --help` は人が使うコマンドとして katachi lint を案内する（katachi-script の Messages.kt:972・1067）。v0.4.0 のリリースノートは「katachi CLI」の項目で lint を説明し、See 先をこのページにしているが、コマンドとしての lint は日本語の docs のどこにも出てこない。英語のページも同じ。

- 同じ指摘: 同じページの L206（まとめ）、ja/guides/cli-and-script.mdx L37（概要のページのカード）
- 直し方の案: コマンドリストに lint の行と節（何を挙げるか、検査を失敗にはしないこと）を足す。`<ファイル>.katachi.kts` の実行は katachi-script のページへのリンクで足りる
- 出どころ: 手順 5 の 5、手順 4 の memo（新機能の See 先のページに lint が無い、priority 3）

## [p4] katachi lint が CLI のページに出てこない（まとめ）
- file: ja/guides/cli-and-script/cli.mdx
- line: 206
- quote: `agent` の下のサブコマンドは AI Agent が導入の手順書に沿って使います
- impact: あり: まとめが挙げるコマンドに lint が無い
- recommend: 直してもらう 8, 直さない 2

「まとめ」の箇条書きは doctor・scaffold・agent だけを挙げ、lint が無い。コマンドリスト（L25-29）と同じ指摘。英語のページも同じ。

- 同じ指摘: 同じページの L29（コマンドリスト）、ja/guides/cli-and-script.mdx L37（概要のページのカード）
- 直し方の案: コマンドリストに lint を足すときに、ここにも「lint で定義の形を見直す候補を列挙します」のように 1 行足す
- 出どころ: 手順 5 の 5

## [p4] katachi lint が CLI のページに出てこない（概要のページのカード）
- file: ja/guides/cli-and-script.mdx
- line: 37
- quote: description="doctor / scaffold / agent の各コマンドとオプションを確認しましょう。"
- impact: あり: カードが挙げるコマンドに lint が無い
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

katachi CLI のページへのカードの説明が「doctor / scaffold / agent の各コマンド」で、lint が無い。CLI のページに lint を足すなら、ここもそろえる。英語のページも同じ。

- 同じ指摘: ja/guides/cli-and-script/cli.mdx L29（コマンドリスト）・L206（まとめ）
- 直し方の案: 「doctor / scaffold / lint / agent の各コマンドとオプションを確認しましょう。」
- 出どころ: 手順 5 の 5

## [p4] ツールを 2 つ以上使うと [AmbiguousLayout] の警告が出ることを、レシピが言わない
- file: ja/recipes/ai-agent.mdx
- line: 16
- quote: 複数のツールの group を使うと、`AGENTS.md` は複数の Role に一致します。
- impact: あり: レシピを 2 つ写すと警告が出るが、放っておいてよいのか直すのかが分からない
- recommend: 直してもらう 7, 直さない 4

同じパターンを 2 つの Role が宣言すると、`[AmbiguousLayout]` の警告が出る（検査は失敗しない。katachi/src/main/kotlin/me/tbsten/katachi/check/LayoutWarnings.kt）。レシピは `"**/AGENTS.md".file()` を 7 つのツールの group に書いていて（L50・145・248・344・504・581・650）、2 つ以上写すと警告が出るが、本文は「一致します」としか言わない。写しで動かして確かめた（`Katachi check found 1 warning. Warnings never fail the check.` と `[AmbiguousLayout] **/AGENTS.md`）。`[AmbiguousLayout]` は日本語の docs に 1 度も出てこない。英語のページも同じ。

- 関連: 同じ行の「使うツールの分だけ呼びます。」への指摘は手順 11 のファイルにある（別の指摘）
- 直し方の案: 「このとき `[AmbiguousLayout]` の警告が出ます（検査は失敗しません）。気になるなら `AGENTS.md` を 1 つの Role にまとめます」のように 1 文足す
- 出どころ: 手順 5 の 6（動かして確定）

## [p4] gradle() が wrapper の 4 ファイルを必須にすることと、外し方（requireWrapper = false）が書かれていない（要確認）
- file: ja/recipes/gradle.mdx
- line: 14
- quote: `gradle()` を1行書くと、Gradle の wrapper・ビルドスクリプト・version catalog が宣言されます。
- impact: あり: gradlew.bat などが無いプロジェクトでレシピどおりに書くと [MissingFile] になり、外し方を docs から探せない（要確認）
- recommend: 直してもらう 7, 直さない 3

`gradle()` は gradlew・gradlew.bat・gradle-wrapper.jar・gradle-wrapper.properties の 4 つを必須にし、1 つでも無いと `[MissingFile]` で検査が失敗する。必須を外すのは `gradle(requireWrapper = false)`（katachi/src/main/kotlin/me/tbsten/katachi/dsl/gradle/GradleGroup.kt:25-28・91・107-114）で、requireWrapper は KDoc にしか出てこない。動かして見つけた: gradlew.bat の無いプロジェクトで `[MissingFile]` になり、How to fix は宣言を消すよう言うが、消せる宣言は `gradle()` の行そのもの。英語のページも同じ。

- 直し方の案: 「wrapper の 4 ファイルは必須です。無い、または一部だけのプロジェクトでは `gradle(requireWrapper = false)` と書きます」を 1 文足す。skill（3-2・fix-violations）と How to fix の文面は別の経路で直す
- 出どころ: 手順 5 の 12（動かして見つけた）

## [p3] 横長の Mermaid の図が縮んで、字が読めない（baseline の流れの図）
- file: ja/guides/baseline.mdx
- line: 21
- quote: flowchart LR
- impact: あり: 図の字が幅 1280 で約 7px、幅 390 で約 3.6px になる
- recommend: 直してもらう 7, 直さない 3

図 1（6 つのノードを横 1 列に並べた flowchart LR）は、viewBox の幅 1377px を本文の幅に縮めて出すので、字が幅 1280 で約 7.1px、幅 390 で約 3.6px になる（ライト・ダークとも）。幅 1280 は目を凝らせば読める程度、幅 390 は読めない。横スクロールにならず、拡大する手段も無い。図の直後のキャプション（L30）が流れを文で言い直しているので、内容は図が読めなくても伝わる。英語のページも同じ（guides/baseline.mdx L20-21）。

- 同じ指摘: ja/recipes/android-three-layer.mdx L29
- 直し方の案: 図を縦（flowchart TB）にする。または CSS で Mermaid の SVG を縮めず、図の枠の中で横スクロールさせる
- 出どころ: 手順 6 の ja-02（priority 3）・en-01
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/problem-baseline-mermaid-390.png

## [p3] ダークで、畳んだコードの「Show more」ボタンの字が背景に沈む（コントラスト比 約 1.9）
- file: docs/ec.config.mjs
- line: 16
- quote: pluginCollapsible({
- impact: あり: ダークの利用者が、15 行を超えるコードの続きを開くボタンに気づきにくい
- recommend: 直してもらう 8, 直さない 2

畳んだコードのボタン（expressive-code-collapsible の .ec-collapse__toggle）の字の色は `var(--ec-collapse-accent-color, var(--sl-color-accent, …))` で、テーマ（nova）のダークの --sl-color-accent が暗い灰色（rgb(74,85,101)）なので、画素から測ったコントラスト比は約 1.8〜2.1（WCAG AA は 4.5。ライトは約 12）。pluginCollapsible に色の指定が無く、docs/src/styles にも上書きが無い。日本語・英語とも、畳んだコードのある全ページで同じ（guides/baseline・get-started/motivation・recipes/gradle で確かめた）。

- 直し方の案: docs/src/styles/custom-global.css で、ダークのときの --ec-collapse-accent-color を明るい色にする。または .ec-collapse__toggle の color を上書きする
- 出どころ: 手順 6 の en-05・ja-04
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/problem-collapse-button-light-vs-dark.png

## [p3] 直前の文が説明する SettingsScript の Role が、畳まれて初期表示に無い
- file: ja/recipes/gradle.mdx
- line: 65
- quote: "BuildScript".role {
- impact: あり: 「Role を分けます」の 2 つ目が、Show more を押さないと見えない
- recommend: 解決済みにする 7, 直してもらう 6, 直さない 2

「build.gradle.kts と settings.gradle.kts は役割の違うファイルなので、Role を分けます。」の直後のコード（L64-82、17 行）が、ec.config.mjs のしきい値（15 行を超えると先頭 8 行だけ見せる）で畳まれる。見えるのは BuildScript の Role の途中までで、分けた 2 つ目の SettingsScript は全部が Show more の奥にある。幅 1280・390 とも同じ。英語のページも同じ行数（recipes/gradle.mdx L64-82。画面では確かめていない）。

- 同じ種類の指摘: ja/guides/baseline.mdx L56（手順 2 のコード）・L109（まとめ）
- 直し方の案: フェンスの `kt` の後ろに `nocollapse` を付ける。または BuildScript と SettingsScript を 2 つのコードブロックに分ける
- 出どころ: 手順 6 の ja-05
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/problem-gradle-settingsscript-collapsed-1280.png

## [p3] 直前の文が指す baselineFile() の定義が、畳まれて初期表示に無い（手順 2 のコード）
- file: ja/guides/baseline.mdx
- line: 56
- quote: val projectArchitecture = architecture {
- impact: あり: 「BaselineFile という名前の Role を用意して」の Role が、Show more を押さないと見えない
- recommend: 解決済みにする 7, 直してもらう 6, 直さない 2

手順「2. Baseline ファイル自身を layout { } で宣言する」のコード（L55-76、20 行）が畳まれる。見えるのは `architecture { … "Tool".group { "Katachi".group { baselineFile() } }` までで、直前の文「慣例に従って BaselineFile という名前の Role を用意して…」が指す `fun DeclarationContainerScope.baselineFile() = "BaselineFile".role { … }` の定義は全部が奥にある。英語のページも同じ形。

- 同じ種類の指摘: 同じページの L109（まとめ）、ja/recipes/gradle.mdx L65
- 直し方の案: フェンスの `kt` の後ろに `nocollapse` を付ける
- 出どころ: 手順 6 の ja の「確かめる途中で見つけた、一覧に無いもの」1（priority 3 相当）
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/verify/collapse-baseline-light.png

## [p3] 英語のページの画像の中の見出しと説明が日本語（重みの比較の図）
- file: guides/diff-report.mdx
- line: 92
- quote: (../../../assets/diff-report-weights.png)
- impact: あり: 英語のページの図に日本語の説明が写っていて、英語の読み手に読めない
- recommend: 直してもらう 7, 直さない 3

「Weights」の節の画像は日本語版と同じ docs/src/assets/diff-report-weights.png で、図に後から足した見出しと説明（「重みなし」「RoleDefinition の 27 行は、他の Role と同じ基準で薄い」「diffWeight = 10.0 を RoleDefinition に」「同じ 27 行の変更が 270 行分として扱われ、×10 の印と濃い色が付く」）が日本語のまま出る。表の部分（Group / Role / File ほか）と alt は英語。色の濃さと ×10 の印は字が読めなくても見て分かる。英語のページだけの問題。

- 直し方の案: 見出しと説明を英語にした画像を docs/src/assets/ に足し、英語のページはそちらを参照する。または図の中の説明を消して、本文と alt に任せる
- 出どころ: 手順 6 の en-03
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/en/problem-diff-report-weights-ja-1280.png

## [p3] FileTree のプレースホルダーが空白で切れ、後半がコメントの色で出る（Without groups の根）
- file: guides/document-generation.mdx
- line: 184
- quote: - \<output directory>/
- impact: あり: プレースホルダーが途中で色が変わって出る（意味は読み取れる）
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

Starlight の FileTree は、名前のあとの空白から先をコメントとして扱う。「Generated files」の 1 つ目の木の根が、名前「<output」と灰色のコメント「directory>/」に分かれて出る（幅 1280・390 とも）。日本語版は「<出力先ディレクトリ>/」と空白が無いので起きない。英語のページだけの問題。

- 同じ指摘: 同じファイルの L192（With groups の根）・L198（nested group）
- 直し方の案: 空白をやめる（`\<output-directory>/` など）
- 出どころ: 手順 6 の en-04
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/en/verify/en04-filetree-1-1280.png

## [p3] FileTree のプレースホルダーが空白で切れ、後半がコメントの色で出る（With groups の根）
- file: guides/document-generation.mdx
- line: 192
- quote: - \<output directory>/
- impact: あり: プレースホルダーが途中で色が変わって出る（意味は読み取れる）
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

2 つ目の木（With groups）の根も、名前「<output」と灰色のコメント「directory>/」に分かれて出る。L184 と同じ原因。

- 同じ指摘: 同じファイルの L184（Without groups の根）・L198（nested group）
- 直し方の案: L184 と同じ書き方にそろえる
- 出どころ: 手順 6 の en-04
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/en/verify/en04-filetree-2-1280.png

## [p3] FileTree のプレースホルダーが空白で切れ、後半がコメントの色で出る（nested group）
- file: guides/document-generation.mdx
- line: 198
- quote: - \<nested group>/
- impact: あり: プレースホルダーが途中で色が変わって出る（意味は読み取れる）
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

2 つ目の木の中の「<nested group>/」が、名前「<nested」と灰色のコメント「group>/」に分かれて出る。同じ木の「<group>/」「<Role>.md」は正しく出る。日本語版は「<ネストしたグループ名>/」で起きない。

- 同じ指摘: 同じファイルの L184・L192（output directory）
- 直し方の案: 空白をやめる（`\<nested-group>/` など）
- 出どころ: 手順 6 の en-04
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/en/verify/en04-filetree-2-1280.png

## [p3] リリースノートが katachi skill の See 先にしているページに、skill の語が出てこない（要確認）
- file: ja/get-started/first-architecture.mdx
- line: 64
- quote: AI Agent が参照するページには、プロジェクトへの katachi の導入を **あなたと一緒に** 進めるよう指示が書いてあります。
- impact: あり: リリースノートから来た人が、このページで katachi skill の説明を見つけられない（要確認）
- recommend: 直さない 6, 直してもらう 5

v0.4.0 のリリースノートは、新機能「katachi skill (Agent Skill)」の See 先をこのページにしているが、ページに「skill」の語が 1 度も無い。「AI Agent に任せる」のタブは katachi CLI のインストールと、install/ja/index.md を渡すプロンプトを案内するだけで、skill が入ること（`gh skill install TBSten/katachi katachi`）は index.md の側にしか書かれていない。手順としては今のままで成り立つ（プロンプトのとおりにすれば、エージェントが index.md から skill へ進む）。英語のページも同じ。

- 直し方の案: 手順 2 の説明に「AI Agent は katachi skill（Agent Skill）を入れて、その手順に沿って進めます」のように 1 文足す。docs に足さないなら、このままでよい（ノートの側は成り立つ）
- 出どころ: 手順 4 の memo（新機能の See 先のページに skill の語が無い。docs に足すかは人の判断）

## [p2] 「まとめ」の完成形のコードが畳まれる（ほかのガイドのまとめは nocollapse）
- file: ja/guides/baseline.mdx
- line: 109
- quote: // 記録する: ./gradlew :architecture-test:test -Dkatachi.baseline.update=true
- impact: なし: 見た目だけ。Show more を押せば全部読める。ほかのガイドとの不ぞろい
- recommend: 解決済みにする 7, 直してもらう 6, 直さない 3

「ここまでの設定を1つにした完成形です。」の直後のコード（L108-130、21 行）が畳まれ、見えるのは 2 行のコメントから `"Katachi".group {` まで。baselineFile() の呼び出しと定義は Show more の奥にある。layout.mdx:170・processor.mdx:344・document-generation.mdx:531・generate-code-from-template.mdx:248 の「まとめ」のコードはどれも `nocollapse` 付きで、baseline だけ付いていない。過去の判断「直さない: 完成形と書く例に import が無い」は import の話で、別の指摘。

- 同じ種類の指摘: 同じページの L56（手順 2 のコード）、ja/recipes/gradle.mdx L65
- 直し方の案: L108 のフェンスの `kt` の後ろに `nocollapse` を付ける
- 出どころ: 手順 6 の ja-06
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/problem-baseline-summary-collapsed-light.png

## [p2] details「type 宣言の詳細を指定する」の表の 1 列目が幅 64px に潰れ、2〜3 文字ずつ折れる
- file: ja/guides/kt-file-content.mdx
- line: 229
- quote: <table>
- impact: なし: 見た目だけ。既定で閉じた details の中で、意味は読み取れる
- recommend: 直してもらう 6, 解決済みにする 5, 直さない 4

details を開くと、表の 1 列目が幅 64px しかなく、「やり／たい／こと」「親ク／ラス／を指／定」「複数種／類を／許容／する」と折れる（幅 1280・390 とも。幅 1920 では折れない）。2 列目のコードブロックが幅を取り切るため。同じページの L337 からの表は `<th style="min-width: 8rem">` があり折れない。過去の判断「直さない: 2 行だけの表「type 宣言の詳細を指定する」」は、表にするかどうかへの判断で、列の潰れには触れていない。表を残す判断と両立する別の指摘として残した。

- 直し方の案: L232 の 1 列目のセルに `style="min-width: 8rem"` を付ける（L340 と同じ）
- 出どころ: 手順 6 の ja-07
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/problem-ktfilecontent-type-table-1280.png

## [p2] トップページのヒーローの例だけ group 名が小文字（"domain".group）のまま
- file: docs/src/components/home/strings.ts
- line: 29
- quote: "domain".group {
- impact: あり: トップページで最初に目に入るコード例が、docs の決まり（group 名は大文字で始める）と違う
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 1

ヒーローの ProjectArchitecture.kt の 2 行目が `"domain".group {`。docs/AGENTS.md の「文章の方針」は group 名を大文字で始めると決めていて、KATACHI-350 で本文の例は `"Domain".group` にそろえたが、トップページの文字列が残った。katachi lint も小文字で始まる group 名を指摘する。英語のトップページも同じ文字列を使う（L139 の `code: ja.hero.code`）。コンパイルは通る。

- 直し方の案: `"Domain".group {` にする（layout の中の "core/domain" は実際のパスなのでそのまま）
- 出どころ: 手順 6 の ja-09
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/problem-home-hero-group-1280.png

## [p2] 幅 390 のトップページで、木の注記（← 置き場所が違う）が箱の右で切れる
- file: docs/src/components/home/strings.ts
- line: 49
- quote: ← 置き場所が違う
- impact: あり: 幅 390 のトップページで、図の要点の注記が初期表示で切れる（箱の中を横に動かせば読める）
- recommend: 直さない 6, 直してもらう 5

幅 390 で、「崩れるのはいつも1ファイルから。」の木の箱（幅 298 に対して中身 357）が「← 置き場所」「← 役割がな」で切れる。overflow-x: auto なので箱の中を横に動かせば読め、ページ全体の横はみ出しは無い。下に「この2つを、誰も検査していない」があるが、どの 2 つかを示す注記が切れる。同じページのほかのコードも幅 390 では横スクロールになるので、コードとしては通常の挙動。英語の木（L148-153）も注記を右に置く同じ作り。

- 同じ指摘: 同じファイルの L62（「宣言した場所だけが存在を許される」の木）
- 直し方の案: 桁そろえの空白を詰めるか注記を短くする。または幅が狭いときだけ木の字を小さくする（docs/src/styles/home.css）
- 出どころ: 手順 6 の ja-10
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/verify/home-pre1-390.png

## [p2] 幅 390 のトップページで、木の注記（✓ declared・✕ reported）が箱の右で切れる
- file: docs/src/components/home/strings.ts
- line: 62
- quote: ✓ declared
- impact: あり: 幅 390 のトップページで、図の注記が初期表示で切れる（印と凡例で意味は伝わる）
- recommend: 直さない 7, 直してもらう 4

幅 390 で、「宣言した場所だけが存在を許される」の木の箱（幅 298 に対して中身 359）が「✓ dec」「✕ rep」で切れる。✓ と ✕ の印は見えていて、下に「宣言済み / 違反として報告される」の凡例があるので意味は伝わる。箱の中を横に動かせば読める。英語のトップページも同じ文字列を使う（L163 の `tree: ja.solution.tree`）。

- 同じ指摘: 同じファイルの L49（「崩れるのはいつも1ファイルから。」の木）
- 直し方の案: L49 と同じ（空白を詰める、または幅が狭いときだけ字を小さくする）
- 出どころ: 手順 6 の ja-10
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/verify/home-pre2-390.png

## [p2] 英語のページに全角の括弧が残っている（KDoc の行）
- file: guides/kt-file-content/kotlin-mapping.mdx
- line: 637
- quote: KDoc（
- impact: なし: 表記だけ。意味は通じる
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

英語のページの「Body and KDoc」の表の 1 行目が、全角の括弧で出る。日本語版の同じ行（ja/guides/kt-file-content/kotlin-mapping.mdx L637）をそのまま写した跡で、同じ表のほかの行は半角の括弧（Anonymous object (object : Listener { … })）。英語の 28 ページで、かな・漢字・全角記号が残るのはこの行と L697 の 2 か所だけ。

- 同じ指摘: 同じファイルの L697（読点「、」）
- 直し方の案: 括弧を半角にする（KDoc のあとに半角スペースと半角の括弧）
- 出どころ: 手順 6 の en-06
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/en/verify/en06-kdoc-viewport-1280.png

## [p2] 英語のページに読点「、」が残っている（detekt、Konsist）
- file: guides/kt-file-content/kotlin-mapping.mdx
- line: 697
- quote: <td>detekt、Konsist</td>
- impact: なし: 表記だけ。意味は通じる
- recommend: 直してもらう 8, 解決済みにする 5, 直さない 2

英語のページの「What ktFileContent cannot express」の表の「Contents of a declaration body」の行が「detekt、Konsist」と読点で出る。日本語版の同じ行をそのまま写した跡。

- 同じ指摘: 同じファイルの L637（全角の括弧）
- 直し方の案: 「detekt, Konsist」にする
- 出どころ: 手順 6 の en-06
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/en/verify/en06-detekt-viewport-1280.png

## [p2] GitHub 宛ての LinkCard（サムネ付き）で、見出しが語の途中で折れる
- file: docs/src/components/LinkCard.astro
- line: 92
- quote: overflow-wrap: anywhere;
- impact: なし: 見た目だけ。本番で GitHub の OGP 画像が出たときにだけ起きる（本番では未確認）
- recommend: 直さない 6, 直してもらう 5

2 列の CardGrid に置いた GitHub 宛ての LinkCard は、右に 80px のサムネが入ると見出しの幅が約 141〜145px になり、語の途中で折れる（guides/basic-api「ProjectArchitect / ure.kt in / sample/jvm」、guides/layout「IdeCodeStyleRol / e.kt in / sample/jvm」、guides/processor は 4 行）。幅 1280 のとき。幅 390 は 1 列になり語の途中では折れない。この環境からは repository-images.githubusercontent.com に届かないので、代わりの画像を返して再現した。画像が読めないとサムネの枠ごと消えて普通のカードになり、問題は起きない。本番で画像が出たときの見た目は Mac で確かめてほしい。日本語のページも同じ部品を使う。

- 直し方の案: 2 列の CardGrid の中ではサムネを出さないか小さくする。または見出しの字を小さくする（見出しの語が 141px に収まらないので、折り方の指定を変えるだけでは直らない見込み）
- 出どころ: 手順 6 の en-07
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/en/problem-linkcard-thumb-basic-api-1280-0.png

## [p2] LinkCard のサムネが cover で切り抜かれ、すぐ上のコメント（contain）と食い違う
- file: docs/src/components/LinkCard.astro
- line: 109
- quote: object-fit: cover;
- impact: なし: 見た目だけ（サムネの切り抜き）と、コードのコメント
- recommend: 直さない 6, 直してもらう 4

recipes/android-three-layer の先頭の LinkCard「App Architecture Guide」のサムネは、横長の OGP 画像（1201x676）を 80x80 に object-fit: cover で切り抜くので、ロゴの文字が「velopers」と途中だけ写る（幅 1280・390 とも。日本語のページも同じ）。すぐ上の L104 のコメントは「contain なので余白ができる」と書いていて、コメントと実装が食い違う。ja の巡回は「正方形・cover の仕様」と見て問題に数えていない。

- 直し方の案: 意図が cover なら L104 のコメントを直す。意図が contain なら `object-fit: contain` にする
- 出どころ: 手順 6 の en の「確かめる途中で見つけた、一覧に無いもの」2（priority 2 程度）
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/en/verify/extra-android-linkcard-thumb-1280.png

## [p2] README の「モジュール」の表に katachi-konture が無い（要確認）
- file: README.ja.md
- line: 134
- quote: | モジュール
- impact: あり: 公開されるモジュール katachi-konture が、README のモジュールの表から探せない（要確認）
- recommend: 直さない 7, 直してもらう 4

「モジュール」の表（L134-138）は katachi・katachi-konsist・Gradle plugin の 3 行で、katachi-konture が無い。モジュールは公開の設定になっていて（katachi-konture/build.gradle.kts）、比較のページは「統合あり（katachi-konture）」と書くが、README にも日本語のガイドにも使い方が無い（ja/guides/ に Konture のページは無い）。0.3 から同じ。比較のページの側の指摘は手順 11 のファイルにある（別の場所）。

- 直し方の案: 表に katachi-konture の行（`konture { }` を使うときだけ）を足す。使い方のページができるまで案内しないなら、このまま
- 出どころ: 手順 5 の 10（priority 2・要確認）

## [p1] Tabs の 5 つのラベルが幅 1280 でも 1 行に収まらず、4 つ目の途中で切れる
- file: ja/guides/document-generation.mdx
- line: 63
- quote: <Tabs>
- impact: なし: 見た目だけ。横スクロールとキーボードで 5 つ目まで届く
- recommend: 直さない 9, 直してもらう 1

「ドキュメント生成を実行する」の Tabs（ディレクトリ構造 / README.md / Domain/README.md / Domain/UseCase.md / Domain/RepositoryInterface.md）は、タブの列 984px に対して枠が 640px で、幅 1280 でも 4 つ目が「Domain/UseCas」で切れ、5 つ目は横にスクロールしないと見えない。横スクロールバーは出て、キーボードの → でも 5 つ目まで移れる。同じ種類の指摘は android-three-layer で「直さない」になっている（過去の判断「Tabs の 8 つのラベルが 1 行に収まらず、1280px でも末尾が切れる」）。こちらは別のファイルなので、同じ扱いでよいかだけの確認。

- 直し方の案: 直さない（android-three-layer と同じ扱い）。直すならラベルを短くする
- 出どころ: 手順 6 の ja-08
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/problem-docgen-tabs-1280.png

## [p1] 「markdown のドキュメント」が小文字（同じ details の中のラベルは Markdown）
- file: ja/get-started/motivation.mdx
- line: 141
- quote: markdown のドキュメントを生成できます。
- impact: なし: 表記ゆれ。既定で閉じた details の中の 1 語
- recommend: 解決済みにする 7, 直してもらう 5, 直さない 3

details「ドキュメントを生成する」の中の文が「markdown のドキュメント」と小文字。今回 document-generation.mdx は「Markdown ドキュメント」にそろえてあり、同じ details の L174 のラベルも「生成された Markdown」。

- 同じ指摘: ja/guides/diff-report.mdx L24
- 直し方の案: 「Markdown のドキュメントを生成できます。」
- 出どころ: 手順 6 の ja-13
- 証拠: /home/claude/katachi/.local/release-v0.4.0/screenshots/ja/problem-motivation-markdown-lowercase.png

## [p1] 「html, markdown 形式のレポート」が小文字（同じページのほかは HTML と Markdown）
- file: ja/guides/diff-report.mdx
- line: 24
- quote: html, markdown 形式のレポートを生成することができます。
- impact: なし: 表記ゆれ
- recommend: 解決済みにする 6, 直さない 5, 直してもらう 4

「html, markdown 形式のレポート」と小文字で出る。同じページの description（L3）と出力ファイルの表（L38-39）は「HTML」「Markdown」。形式の名前の並びとして意図した書き方かもしれない。

- 同じ指摘: ja/get-started/motivation.mdx L141
- 直し方の案: 「HTML, Markdown 形式のレポート」
- 出どころ: 手順 6 の ja-13

## [p1] showcase のページが TODO のまま（draft なので公開されない）
- file: ja/showcase.mdx
- line: 17
- quote: TODO
- impact: なし: draft: true のページで、公開されない
- recommend: 直さない 8, 直してもらう 2

「cream の例」の節の本文が TODO の 1 語だけ。frontmatter が draft: true なので、ビルドしたサイトには出ない。書きかけの残り。

- 直し方の案: v0.4.0 では直さない。ページを公開するときに書く
- 出どころ: 手順 5 の 11（priority 1）
