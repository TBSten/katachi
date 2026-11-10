---
title: v0.4.0 リリース前チェック 手順 11（ドキュメントのルール適合・p4 以下）
base: aaff0ea
---

## [p4] 「組み込みの ArchitectureProcessor」の表で長いコードが列を押し広げ、右端の列が切れる
- file: ja/guides/processor.mdx
- line: 86
- quote: projectArchitecture.assert(FileConstraintCheck())
- impact: あり: 公開ページの表示（読みにくい）。英語版 /guides/processor/ では幅 833px で本文の幅 640px を超え、「What it does」列が右端で切れる
- recommend: 直してもらう 6, 直さない 3

**ルールに反する点**: 手順 6 の巡回から。processor の表の `FileConstraintCheck` の行が、折り返せない長いコード `projectArchitecture.assert(FileConstraintCheck())` を含むため、表が本文の幅を超えて右端の列が切れる。

**直し方の案**: この例を表の外（表の下の短いコード例）に出す、または表のセルには `FileConstraintCheck` の説明だけを書いて渡し方は別の文にする。

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 59
- quote: ".claude/skills/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 91
- quote: ".claude/hooks/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 153
- quote: ".cursor/skills/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 256
- quote: ".devin/skills/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 297
- quote: ".windsurf/skills/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 352
- quote: ".github/skills/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 440
- quote: ".cline/skills/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 456
- quote: ".cline/hooks/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 512
- quote: ".junie/skills/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L589, ja/recipes/ai-agent.mdx L660

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 589
- quote: ".agents/skills/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L660

## [p4] ai-agent: skills・hooks の Role が `**/*`（ディレクトリの中の何でも）になっている
- file: ja/recipes/ai-agent.mdx
- line: 660
- quote: ".agents/skills/**/*".file()
- impact: あり: 写した利用者が、何でも許す Role を足す形を覚える（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（「ここにあれば何でも」の layout を書かない。名前のパターンか中身の制約を付ける）／B3（skill のディレクトリには `SKILL.md`・スクリプト・参照 md など複数の種類が入る）／B11（ツールが構成を決める場所は例外にできるが、何のためかを例の中に書く）。根拠: layout「任意のファイルの配置を許す」の Aside、skill 3-2「Do not stop at placement」

**直し方の案**: ツールが中身を決める場所だと分かるコメントを 1 行（例 `// スキルのディレクトリの中身はツールが決める`）、または skill は `.claude/skills/*/SKILL.md` のように名前を持つファイルだけにする。hooks は拡張子（`*.sh`。Cursor の `.cursor/hooks/*.sh` と同じ形）で絞る

同じ指摘: ja/recipes/ai-agent.mdx L59, ja/recipes/ai-agent.mdx L91, ja/recipes/ai-agent.mdx L153, ja/recipes/ai-agent.mdx L256, ja/recipes/ai-agent.mdx L297, ja/recipes/ai-agent.mdx L352, ja/recipes/ai-agent.mdx L440, ja/recipes/ai-agent.mdx L456, ja/recipes/ai-agent.mdx L512, ja/recipes/ai-agent.mdx L589

## [p4] Tabs の 8 つのラベルが 1 行に収まらず、1280px でも末尾が切れる
- file: ja/recipes/android-three-layer.mdx
- line: 114
- quote: <Tabs>
- impact: あり: 公開ページの表示。末尾のタブ（DataSource ほか）が見えず、気づかないまま読み飛ばされる
- recommend: 直してもらう 6, 直さない 3

**ルールに反する点**: 手順 6 の巡回から。Role ごとの 8 つの `<TabItem label="…">`（UiState・ViewModel・Screen・UseCase・DomainModel・RepositoryInterface・RepositoryImplementation・DataSource）が 1 行に収まらず、幅 1280px でも末尾のラベルが切れる。

**直し方の案**: ラベルを短くする（`Repository`・`RepositoryImpl` など）、タブを Role の group（ui・domain・data）ごとの 3 つにまとめる、またはタブをやめて見出しで並べる。

## [p3] README の手順の途中で baseline へ誘っている（D1-12・D3-5）
- file: README.ja.md
- line: 103
- quote: 今ある違反を [baseline]
- impact: あり: 公開される README の本文。「要確認」（README は入口なので段階導入への道を残したい、という判断もありうる）
- recommend: 直してもらう 5, 直さない 5

**ルールに反する点**: D1-12（既存プロジェクトへの段階導入を「初めてのアーキテクチャ定義」以外に書かない）、D3-5（手順の途中で別のページへ誘わない。「初めての定義の手順 5 から `gradle()` と baseline の案内を削った」）。first-architecture.mdx には baseline の案内が無い（grep で確認）ので、README だけが残している

**直し方の案**: 「最初は、定義していないファイルやディレクトリが違反として出ます。出てきたパスごとに、定義に足すかファイルを消すかを決めていきます。」で止め、baseline は下の「もっと知る」の表（L130）に任せる

## [p3] 「だから重なりません」と言い切るが、ktFileContent は Konsist と範囲が重なる
- file: ja/get-started/comparison-with-other-tools.mdx
- line: 36
- quote: だから重なりません。
- impact: あり: 読んだ人が katachi と Konsist の併用の要否を判断する本文。要確認
- recommend: 直してもらう 6, 直さない 3, 解決済みにする 2

**ルールに反する点**: D3-2（言い切れないことは推奨・できることとして書く）。根拠: 同じページの L47〜48・L203 と faq L123〜128 は、`ktFileContent { }`（katachi-konsist）が「宣言の形」を見ると書き、Konsist（宣言の中身や関係）と守備範囲が近い。L24 の表の katachi の行は「ファイルが存在してよいか。どこに置かれるか」だけで、`ktFileContent { }` に触れない

**直し方の案**: 「katachi は存在の可否と配置を問うので、ほかの5つとは見る対象が違います」のように、重ならない範囲を限って書く。または L24 の表に `ktFileContent { }` の担当を足す

## [p3] doctor の出力例に今は出ない文言「ガイドとテンプレートは同梱」がある
- file: ja/guides/cli-and-script/cli.mdx
- line: 43
- quote: ガイドとテンプレートは同梱
- impact: あり: 公開される CLI の出力の写しが、実際の出力（「必要なものはそろっています（katachi <版>）。」）と違う
- recommend: 直してもらう 8, 直さない 1

**ルールに反する点**: 手順 5 から。cli.mdx の `katachi doctor` の実行結果例が「必要なものはそろっています（katachi 0.4.0。ガイドとテンプレートは同梱）。」と書くが、実装（`katachi-script/.../internal/Messages.kt` L75）の出力は「必要なものはそろっています（katachi $scriptVersionCurrent）。」で「ガイドとテンプレートは同梱」は出ない。CLI・IDE の出力の写しは実装と合わせる（D1-14 の趣旨）。

**直し方の案**: 例の 1 行を「必要なものはそろっています（katachi 0.4.0）。」にする。

## [p3] 「いずれも Gradle script で設定が可能」と表が食い違う
- file: ja/guides/diff-report.mdx
- line: 96
- quote: いずれも コマンド 実行時, Gradle script で設定が可能です。
- impact: あり: 利用者が書けないオプションの書き方を探して迷う
- recommend: 直してもらう 8, 直さない 1

**ルールに反する点**: D3-2（言い切れないことを言い切っている）。根拠: L210 の `fullImportanceLines` は「コマンドの --arg でのみ指定できます」で、「いずれも」に当てはまらない。写した利用者が Gradle script に `fullImportanceLines` を書こうとして迷う

**直し方の案**: 「ほとんどのオプションは、コマンド実行時と Gradle script のどちらでも設定できます（`fullImportanceLines` はコマンドだけ）。」。ついでに全角の語間のスペースと半角の「,」も直す（「コマンド 実行時, Gradle script で」→「コマンド実行時と Gradle script で」）

## [p3] 例の Role が手書きの `VersionCatalog`（Gradle のファイル）になっている
- file: ja/guides/document-generation.mdx
- line: 284
- quote: "VersionCatalog".role { }
- impact: あり: 写すと悪い形が広まる（空の Role の断片なので軽い）
- recommend: 直してもらう 5, 直さない 5

**ルールに反する点**: B16（Gradle のファイルは `gradle()` の 1 行で宣言し、手書きの Role にしない）。`documented = false` の見本としては B30 に沿うが、写すと Gradle の Role を手書きする形が広まる

**直し方の案**: Gradle 以外の例（生成物の Role など）にする。`"Build".group` の名前も合わせる

## [p3] テンプレートのパラメータの例が、同じページの capture と同じ名前 `name`
- file: ja/guides/generate-code-from-template.mdx
- line: 112
- quote: val name by stringParameter()
- impact: あり: 公開ページの表。写すと、capture の `name` と同名のパラメータを宣言し直す形になる
- recommend: 直してもらう 7, 直さない 2, 解決済みにする 5

**ルールに反する点**: B24（capture の値は `captureValue("名前")` で読む。同じ名前を `stringParameter()` などで宣言し直さない）。根拠: 同じページの L22・L39 の `name` は `capture("name")` で、L118 の Aside は「capture とパラメータは、名前ごとに 1 つ」と言う。表の `val name by stringParameter()` と `--arg name=User` を写すと、すでに capture にある `name` を宣言し直す形になる

**直し方の案**: 表の例を capture と重ならない名前にする（`val summary by stringParameter()`・`--arg summary=…` など。L254 の例が `summary` を使っている）

## [p3] processor ページが processor を「重要な構成要素」と前面に出している
- file: ja/guides/processor.mdx
- line: 14
- quote: katachi の重要な構成要素の 1 つである ArchitectureProcessor を解説し、katachi の理解を深めます。
- impact: あり: 読んだ利用者が「理解しないと使えない」と誤解する（要確認）
- recommend: 直してもらう 6, 直さない 4

**ルールに反する点**: D1-7（`processor` を前面に出さない。ほとんどの利用者は `assert()` の 1 行しか触らない。ページが自分で短い前提を 2〜3 行持つ）。L42 の「katachi は大きく分けて…2 つの要素でできています」も同じ方向

**直し方の案**: 冒頭に「ほとんどの利用者は `projectArchitecture.assert()` の 1 行しか書かないので、このページは読まなくても katachi を使えます」の趣旨を 2〜3 行で足し、「重要な構成要素」「理解を深めます」を外す

## [p3] android-three-layer: `DomainModel` が `"*".ktFile()` だけ（受け皿の Role）
- file: ja/recipes/android-three-layer.mdx
- line: 255
- quote: "app" / mainSourceSet / kotlin / appPackageDir / "domain/model" / "*".ktFile()
- impact: あり: 3 層の見本として写される（要確認）
- recommend: 直してもらう 4, 直さない 5

**ルールに反する点**: B13（ファイル名のパターンも `ktFileContent { }` も無い、ここにあれば何でもの Role）。同じページの UiState・ViewModel・UseCase などは名前のパターンと `ktFileContent` でファイルの中身まで決めているので、DomainModel だけ形が違う。D2-5（ルールを守った見本）にも数える

**直し方の案**: `ktFileContent { `package`(matchingDirectory); `import`("**"); required(`class`, fileName) { allow(anythingElse) } }` のように、1 ファイルに 1 つの class だけを置く決まりを足す。足さない意図なら summary かコメントに「名前も中身も決めない」と書く

## [p3] gradle: `ConventionPluginSource` が `"**" / "*".ktFile()`（受け皿の Role）
- file: ja/recipes/gradle.mdx
- line: 103
- quote: "**" / "*".ktFile() // パッケージを切ったクラスも許す
- impact: あり: 写すと受け皿の Role が増える形が広まる（要確認）
- recommend: 直してもらう 3, 直さない 6

**ルールに反する点**: B13（`"**/*.kt"` のように「ここにあれば何でも」の layout。ファイル名のパターンか `ktFileContent { }` を付ける）。根拠: skill 3-2「Do not stop at placement」。convention plugin のヘルパーのクラスは名前で絞れないので、ktFileContent を付けない理由が分からない

**直し方の案**: `ktFileContent { }` で最低限（`package`・`import`・`allow(anythingElse)` など）を書くか、コメントで「中身は問わない」と明記する

## [p3] skill が出力の写しを英語だけで書いている: `kotlin is not on PATH`（D1-14）
- file: skills/katachi/references/setup/step0/0-1-permissions.md
- line: 50
- quote: kotlin is not on PATH
- impact: あり: skill は公開される。エージェントが出力と手順を照合できなくなる
- recommend: 直してもらう 8, 直さない 2

**ルールに反する点**: D1-14（skill に `katachi` の出力を書くときは ja / en の 2 通りを並べる）。出力は `katachi-script/.../internal/Messages.kt:96-97` に ja（「kotlin が PATH にありません。brew install kotlin で入れてください。」）と en がある。0-2 は「checklist の言語で答える」と言うが、この時点では checklist が無く、`--lang` を付けないと環境の言語になる。日本語の出力が出ると、この行と合わせて読めない

**直し方の案**: 「`kotlin is not on PATH`（ja: `kotlin が PATH にありません`）」と並べる

## [p3] skill が出力の写しを英語だけで書いている: Kotlin が古いときの doctor の警告（D1-14）
- file: skills/katachi/references/setup/step0/0-2-working-directory.md
- line: 57
- quote: This project uses Kotlin 1.9.10
- impact: あり: skill は公開される
- recommend: 直してもらう 8, 直さない 2

**ルールに反する点**: D1-14。同じ行の `"Gradle needs upgrading too"` も英語だけ。`Messages.kt:262-265`・`:283-290` に ja（「このプロジェクトの Kotlin は … です。katachi は Kotlin … 以降が要ります」「Gradle も上げる必要があります」）がある。0-2 の L5 は「checklist の言語（ja）で `katachi` が答える」と書くので、日本語のユーザではここが日本語の出力になる

**直し方の案**: 両方に ja の文言を並べる（「このプロジェクトの Kotlin は 1.9.10 です。katachi は Kotlin 2.2 以降が要ります」「Gradle も上げる必要があります」）

## [p2] README の例の group 名が小文字始まり（B29）
- file: README.ja.md
- line: 84
- quote: "domain".group {
- impact: あり: README の例を写すと、AGENTS の決まりに反する形が広まる
- recommend: 直してもらう 8, 直さない 2

**ルールに反する点**: B29（group 名は大文字で始める: `"Domain".group { }`）。L86 の layout のパス `"domain/src/main/kotlin/..."` は実際のパスなので変えない（`"Domain".group` に直すのは L84 だけ）

**直し方の案**: `"Domain".group {`

## [p2] 「うまく動かないとき」が出力を 1 行ずつ解説している（D1-8・要確認）
- file: README.ja.md
- line: 114
- quote: Failed to load JUnit Platform
- impact: あり: README の本文。要確認
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: D1-8（エラーメッセージの読み方をドキュメント化しない）。L112〜118 の 4 項目は「出力 → 原因」の対応表で、`Failed to load JUnit Platform`・`Cannot create Launcher without at least one TestEngine`・`did not discover any tests to execute` は JUnit の出力、`[UncheckedFileConstraint]` だけが katachi の出力。`<details>` に畳んであり（D3-6 の形）、first-architecture.mdx L212 にも `[UncheckedFileConstraint]` は書いてある。katachi のエラーの読み方ではなく、導入で詰まる 4 点の案内とも読める

**直し方の案**: 直さないでよい。外すなら `<details>` ごと first-architecture に任せる

## [p2] FAQ が既存プロジェクト向けに baseline を案内している
- file: ja/get-started/faq.mdx
- line: 69
- quote: すぐには片付けきれない場合は、実験的な機能の baseline（`architecture { baseline() }`）で
- impact: あり: 公開される FAQ の本文。要確認
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: D1-12（既存プロジェクトへの段階導入を「初めてのアーキテクチャ定義」以外に書かない）。根拠: 「既存のプロジェクトに入れたら、違反だらけになりませんか」の答えの後半（L69〜71）が、baseline による段階導入の案内になっている。一方、質問への答えとして自然で、baseline のガイド（実験的な機能）が別にある。ルール側（D1-12 の「baseline ガイドは別」）が線を引いていない

**直し方の案**: 直すなら L69〜71 を「すぐには片付けきれない場合は [baseline](…) もあります」の 1 文にする

## [p2] 手順 1 の設定を、本文で説明し直している
- file: ja/get-started/first-architecture.mdx
- line: 107
- quote: katachi の Gradle plugin は Gradle Plugin Portal ではなく Maven Central に公開しています。
- impact: あり: 公開されるページの手順の文章。要確認
- recommend: 解決済みにする 5, 直さない 5, 直してもらう 3

**ルールに反する点**: D3-13（コード例で分かる設定を本文で説明し直さない。理由はコメント 1 行）、D3-5（利用者が最初に知らなくてよい細部）。根拠: AGENTS の同じ項は、この手順 2 から設定の説明を削った例を挙げている。ただしこの段落は `mavenCentral()` が無いと plugin を解決できないという、写し漏れを防ぐ注意でもある

**直し方の案**: コード例の `pluginManagement { }`・`dependencyResolutionManagement { }` の `mavenCentral()` の行にコメント 1 行（`// katachi は Maven Central に公開している`）を足し、段落を削る

## [p2] ハイライトの範囲が閉じかっこで終わる（basic-api の ktFileContent）
- file: ja/guides/basic-api.mdx
- line: 201
- quote: kt {7-12}
- impact: なし: 意味の変わらないハイライトの作法
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: D2-4（ハイライトは閉じかっこを指さない。ブロックの 12 行目は `ktFileContent {` を閉じる `}`）

**直し方の案**: `{7-11}` にする

## [p2] group 名が小文字の例（重みの `sample=0.5`）
- file: ja/guides/diff-report.mdx
- line: 170
- quote: sample=0.5
- impact: あり: 小文字の group 名が広まる（コンパイルは通る）
- recommend: 直してもらう 7, 直さない 3

**ルールに反する点**: B29（group 名は大文字で始める）。根拠: 同じページの定義例の group は `Domain`・`Testing`。`weights` に書く値は group の修飾名なので、小文字だと写した人が小文字の group 名を作る

**直し方の案**: `Sample=0.5`（目立たせない）

## [p2] 「プラグインを当てた」
- file: ja/guides/diff-report.mdx
- line: 259
- quote: プラグインを当てた全モジュールで実行します
- impact: あり: 意味は通じるが、書き方の決まりから外れる
- recommend: 解決済みにする 6, 直してもらう 5, 直さない 2

**ルールに反する点**: D3-10 と同じ趣旨（「当てる」「当たる」を使わない。共通の前提）。lint:text の対象外の表の中

**直し方の案**: 「プラグインを適用した全モジュールで実行します」

## [p2] ドキュメント生成のガイドで自作 processor の内部の話に触れている
- file: ja/guides/document-generation.mdx
- line: 288
- quote: 自作の processor でこの値を見るときは、`Role.groupPath` をたどって親の group の値も見てください。
- impact: あり: 読み手に不要な情報（要確認）
- recommend: 直してもらう 5, 直さない 5

**ルールに反する点**: D3-5（利用者が最初に知らなくてよい細部・内部の仕組み）、D1-7（processor を前面に出さない）。`<details>` の中なので軽い

**直し方の案**: 一文を削る（要るなら `documented` の API リファレンスに任せる）

## [p2] group 名が小文字始まり（`feature.Screen` の group `feature`）
- file: ja/guides/generate-code-from-template.mdx
- line: 78
- quote: feature.Screen
- impact: あり: 公開ページの表。写した利用者が小文字の group 名を広める
- recommend: 直してもらう 8, 直さない 1, 解決済みにする 5

**ルールに反する点**: B29（group 名は大文字で始める。例の修飾名もそろえる）

**直し方の案**: `Feature.Screen`・group `Feature`

## [p2] group 名が小文字始まり（`feature.Screen.preview`）
- file: ja/guides/generate-code-from-template.mdx
- line: 79
- quote: feature.Screen.preview
- impact: あり: 同上
- recommend: 直してもらう 8, 直さない 1, 解決済みにする 5

**ルールに反する点**: B29

**直し方の案**: `Feature.Screen.preview`・group `Feature`（L78 と同時に直す）

## [p2] group 名が小文字始まり（`data.RepositoryInterface`）
- file: ja/guides/generate-code-from-template.mdx
- line: 239
- quote: --arg "template=data.RepositoryInterface"
- impact: あり: 公開ページのコマンド例
- recommend: 直してもらう 8, 直さない 1, 解決済みにする 5

**ルールに反する点**: B29

**直し方の案**: `Data.RepositoryInterface`

## [p2] 「全部使うと」の完成形に import が無く、省いたとも書いていない
- file: ja/guides/generate-code-from-template.mdx
- line: 246
- quote: このページで扱った書き方を全部使うと、テンプレートは次のようになります。
- impact: あり: 要確認。「全部使うと」を完成形と読んだ利用者が、import 無しで写す
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: D2-2（ファイル全体に読める例は import をそろえる。断片で省くなら省いたと書く）／C15（`.template`・`capture()` を使う例）。根拠: 「全部使うと」で締める例で、`"FeatureReadme".role {` から始まる。`import me.tbsten.katachi.dsl.template` などが無い

**直し方の案**: コードの先頭に `// package / import の行は省略` を足す

## [p2] ハイライトの範囲が閉じかっこで終わる（layout の冒頭の例）
- file: ja/guides/layout.mdx
- line: 24
- quote: kt {4-6}
- impact: なし: 意味の変わらないハイライトの作法
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: D2-4（ブロックの 6 行目は `layout {` を閉じる `}`）

**直し方の案**: `{4-5}` にする

## [p2] 同じことを言い換えた箇条書き
- file: ja/guides/processor.mdx
- line: 64
- quote: アーキテクチャを定義しただけでは何も起こりません。その定義を使って何かを行うのが ArchitectureProcessor です。
- impact: あり: 読み手が重複で迷う程度
- recommend: 直してもらう 5, 直さない 5

**ルールに反する点**: D3-5（同じことの言い換え）。L61「アーキテクチャ定義そのものは何も実行しません。…受け取って何かをするのが ArchitectureProcessor」と同じ内容

**直し方の案**: 2 つの箇条書きを削るか、L61 の 1 文にまとめる。L112「いずれもプロジェクトのアーキテクチャ定義を受け取り、検証や生成に使っています。」も表の説明の繰り返しなので一緒に削れる

## [p2] 読み飛ばしても先に進める注意が caution
- file: ja/guides/processor.mdx
- line: 337
- quote: 用意した実行方法は、processor の KDoc などにドキュメントとして書き、実行方法まで利用者に伝えてください。
- impact: あり: 軽微
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: D3-6（読み飛ばされては困る注意だけを Aside に）。読まなくても手順は完了できる推奨事項で、C11 の「実行方法を KDoc に書く」を caution にしている

**直し方の案**: 「2. 実行方法を用意する」の本文に一文として移す。caution のままにするなら、書かないとどうなるか（利用者が `process` を直接呼ぶことになる）を添える

## [p2] 「完成です」と書く例が断片（package・import・opt-in が無い）
- file: ja/guides/processor.mdx
- line: 342
- quote: 独自の processor は、`ArchitectureProcessor` を実装し、`Architecture` の拡張関数として実行方法を用意すると完成です。
- impact: あり: 写した利用者がコンパイルできない（コンパイラが opt-in を案内する）。要確認
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: D2-2（ファイル全体に読める例は import をそろえる。断片なら省いたと書く）、D2-1（ファイル全体なら `@file:OptIn`）。`ArchitectureProcessor` は `@ExperimentalKatachiApi` なので、写すと opt-in の指摘が出る

**直し方の案**: ブロック先頭に `// package / import の行は省略` を足す。ファイル全体の例にするなら `@file:OptIn(ExperimentalKatachiApi::class)` と import を書く

## [p2] ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- file: ja/recipes/ai-agent.mdx
- line: 87
- quote: "Hooks".role {
- impact: あり: 写す人が同じ概念に別名を付ける（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる

**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい

同じ指摘: ja/recipes/ai-agent.mdx L268, ja/recipes/ai-agent.mdx L372, ja/recipes/ai-agent.mdx L452, ja/recipes/ai-agent.mdx L601, ja/recipes/ai-agent.mdx L680

## [p2] ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- file: ja/recipes/ai-agent.mdx
- line: 268
- quote: "Hooks".role {
- impact: あり: 写す人が同じ概念に別名を付ける（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる

**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい

同じ指摘: ja/recipes/ai-agent.mdx L87, ja/recipes/ai-agent.mdx L372, ja/recipes/ai-agent.mdx L452, ja/recipes/ai-agent.mdx L601, ja/recipes/ai-agent.mdx L680

## [p2] ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- file: ja/recipes/ai-agent.mdx
- line: 372
- quote: "Hooks".role {
- impact: あり: 写す人が同じ概念に別名を付ける（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる

**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい

同じ指摘: ja/recipes/ai-agent.mdx L87, ja/recipes/ai-agent.mdx L268, ja/recipes/ai-agent.mdx L452, ja/recipes/ai-agent.mdx L601, ja/recipes/ai-agent.mdx L680

## [p2] ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- file: ja/recipes/ai-agent.mdx
- line: 452
- quote: "Hooks".role {
- impact: あり: 写す人が同じ概念に別名を付ける（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる

**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい

同じ指摘: ja/recipes/ai-agent.mdx L87, ja/recipes/ai-agent.mdx L268, ja/recipes/ai-agent.mdx L372, ja/recipes/ai-agent.mdx L601, ja/recipes/ai-agent.mdx L680

## [p2] ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- file: ja/recipes/ai-agent.mdx
- line: 601
- quote: "Hooks".role {
- impact: あり: 写す人が同じ概念に別名を付ける（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる

**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい

同じ指摘: ja/recipes/ai-agent.mdx L87, ja/recipes/ai-agent.mdx L268, ja/recipes/ai-agent.mdx L372, ja/recipes/ai-agent.mdx L452, ja/recipes/ai-agent.mdx L680

## [p2] ai-agent: 同じ概念の Role 名がグループごとに違う（`Hooks` が設定とスクリプトの両方）（B7・要確認）
- file: ja/recipes/ai-agent.mdx
- line: 680
- quote: "Hooks".role {
- impact: あり: 写す人が同じ概念に別名を付ける（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B7（同じ種類のファイル・同じ概念を、同じページで別の Role 名・別の語で呼ばない）。`Hooks` が、ツールによってスクリプトの Role にも設定ファイルの Role にもなり、Cursor だけ 2 つに分ける。ほかにも指示ファイルが `Rules`（Claude Code・Cursor・Windsurf・Cline・Junie・Codex・Antigravity）・`Instructions`（Copilot）・`Conventions`（Aider）、設定が `Settings`（Claude Code・Aider）・`Config`（Codex・Junie）と揺れる

**直し方の案**: フックの設定ファイルは `HookConfig`、スクリプトは `HookScripts`（Cursor に合わせる）にそろえる。指示・設定の名前は、ツール自身の呼び方なら今のままでよい

同じ指摘: ja/recipes/ai-agent.mdx L87, ja/recipes/ai-agent.mdx L268, ja/recipes/ai-agent.mdx L372, ja/recipes/ai-agent.mdx L452, ja/recipes/ai-agent.mdx L601

## [p2] android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- file: ja/recipes/android-three-layer.mdx
- line: 130
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- impact: あり: 写す人が 8 回書く形を覚える。ただし動作には影響しない（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」

**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい

同じ指摘: ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## [p2] android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- file: ja/recipes/android-three-layer.mdx
- line: 161
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- impact: あり: 写す人が 8 回書く形を覚える。ただし動作には影響しない（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」

**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい

同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## [p2] android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- file: ja/recipes/android-three-layer.mdx
- line: 194
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- impact: あり: 写す人が 8 回書く形を覚える。ただし動作には影響しない（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」

**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい

同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## [p2] android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- file: ja/recipes/android-three-layer.mdx
- line: 224
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- impact: あり: 写す人が 8 回書く形を覚える。ただし動作には影響しない（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」

**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい

同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## [p2] android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- file: ja/recipes/android-three-layer.mdx
- line: 255
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- impact: あり: 写す人が 8 回書く形を覚える。ただし動作には影響しない（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」

**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい

同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## [p2] android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- file: ja/recipes/android-three-layer.mdx
- line: 276
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- impact: あり: 写す人が 8 回書く形を覚える。ただし動作には影響しない（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」

**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい

同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L307, ja/recipes/android-three-layer.mdx L338

## [p2] android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- file: ja/recipes/android-three-layer.mdx
- line: 307
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- impact: あり: 写す人が 8 回書く形を覚える。ただし動作には影響しない（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」

**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい

同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L338

## [p2] android-three-layer: 同じパスの前半を 8 つの Role に書き写している（B14）
- file: ja/recipes/android-three-layer.mdx
- line: 338
- quote: "app" / mainSourceSet / kotlin / appPackageDir
- impact: あり: 写す人が 8 回書く形を覚える。ただし動作には影響しない（要確認）
- recommend: 直さない 6, 直してもらう 3

**ルールに反する点**: B14（何度も出るパスは `val` に切り出す。おすすめ）。`appPackageDir` は切り出してあるが、`"app" / mainSourceSet / kotlin / appPackageDir` の並びが 8 回出る。根拠: layout「パスを定数に切り出す」、skill 3-3「Duplication is shared」

**直し方の案**: `Constants.kt` にモジュールの `val appMainDir = "app" / mainSourceSet / kotlin / appPackageDir` のようなものを置く（`/` の型が layout の外で使えるかは Gradle で確かめたい、下の節）。難しければ今のままでよい

同じ指摘: ja/recipes/android-three-layer.mdx L130, ja/recipes/android-three-layer.mdx L161, ja/recipes/android-three-layer.mdx L194, ja/recipes/android-three-layer.mdx L224, ja/recipes/android-three-layer.mdx L255, ja/recipes/android-three-layer.mdx L276, ja/recipes/android-three-layer.mdx L307

## [p2] 設定項目の問い「1 Role 1 ファイルで書くか」が B3・B4 と読み違えやすい
- file: skills/katachi/assets/ja/check-list.html
- line: 1720
- quote: ['oneRolePerFile', '1 Role 1 ファイルで書くか', 'yes']
- impact: あり: 利用者が設定の表で読む文言。要確認（ルールの側の曖昧さなので、直すなら skill 1-3 と英語版も同時）
- recommend: 直してもらう 4, 直さない 5, 解決済みにする 3

**ルールに反する点**: B3（1 つの Role には 1 種類のファイル）・B4（1 ファイル 1 Role にしない）。ただし問いの意味は skill 1-3 の `oneRolePerFile`「One Role per file?」（B20 の「定義のファイルに Role を 1 つ」の話）なので、コード例の側の違反とは数えず、ルールと skill の側の曖昧さ（E）として報告する

**直し方の案**: 「定義のファイルを Role ごとに分けるか」（英語版は skill 1-3 の表と `assets/en` の `One Role per file?` と合わせて直す）

## [p2] 記入例の group 名が小文字始まり（`app.ui`）（B29・E13・要確認）
- file: skills/katachi/assets/ja/project-code-base-report.html
- line: 1235
- quote: "group": "app.ui",
- impact: なし: HTML コメント内の記入例で画面に出ない（ただしエージェントは写す。要確認）
- recommend: 直してもらう 5, 直さない 5

**ルールに反する点**: B29（group 名は大文字で始める）。ただし skill が docs の決まりに従うかが決まっていない（E13。skill 3-3 は「ディレクトリ名は group 名の先頭を小文字にしたもの」と書き、大文字始まりを前提にする）ので、コード例の側の違反ではなく E13 として報告する

**直し方の案**: `"group": "App.Ui"` のように大文字始まりにする（入れ子の区切りを `.` で書く形が合っているかも確かめる）

## [p2] 記入例の group 名が小文字始まり（`app`）（B29・E13・要確認）
- file: skills/katachi/assets/ja/project-code-base-report.html
- line: 1251
- quote: "group": "app",
- impact: なし: HTML コメント内の記入例（要確認）
- recommend: 直してもらう 5, 直さない 5

**ルールに反する点**: B29・E13（上と同じ。Role `AppBuildFile` の group）

**直し方の案**: `"group": "App"`

## [p2] 6-A〜6-D のファイルの 1 行目が `##`（D1-15）
- file: skills/katachi/references/setup/README.md
- line: 35
- quote: | 6-A |
- impact: あり: skill は公開される。ただし見出しの階層だけで、読み手には届きにくい（要確認）
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: D1-15（各ファイルの 1 行目は `## N.` / `### N-M.`）。README の表の題は 27 ファイルすべてと一致したが、`step6/6-A`〜`6-D`（表の L35〜38）の 1 行目だけが `## 6-A.`〜`## 6-D.` で、同じ N-M の形の 6-0 は `### 6-0.`。6-A〜6-D は「報告のあとの選択」なので別格として `##` にしたとも読める

**直し方の案**: `### 6-A.` などにそろえるか、README に「6-A〜6-D は `##`」と書く（`step6/` は skill-2 の担当なので、そちらでも出うる）

## [p2] skill が出力の写しを英語だけで書いている: scaffold の `already exists`（D1-14）
- file: skills/katachi/references/setup/step0/0-1-permissions.md
- line: 33
- quote: already exists
- impact: あり: skill は公開される。理由の説明なので影響は小さい（要確認）
- recommend: 直さない 5, 直してもらう 5

**ルールに反する点**: D1-14。`Messages.kt:430-433` は ja「がすでに存在します」・en「already exists」。ただしここは「probe でディレクトリを作るな」の理由の説明で、出力と照合する指示ではないので p2

**直し方の案**: 「`already exists`（ja: `がすでに存在します`）」と並べるか、「すでにあると言って止まる」と出力の文言を引用しない形にする

## [p2] 削除済みの `katachi agent init` が旧作業ディレクトリの説明に残っている（B27）
- file: skills/katachi/references/setup/step0/README.md
- line: 33
- quote: katachi agent init
- impact: あり: skill は公開される。旧版からの利用者の目印として残す価値もある（要確認）
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: B27（削除したコマンドを書かない。`katachi agent init` は PR #65 で削除済み）。ただしここは「旧版が作った作業ディレクトリがあったら」の説明で、手順としては案内していない。手順 5 の katachi-script.mdx・cli.mdx とは別の箇所（手順 5 の 3 件には入っていない）

**直し方の案**: 「older katachi が作った作業ディレクトリ（`check-list.html` に今の手順の項目が無いもの）」と、削除済みのコマンド名を出さずに書く

## [p2] ✗/○ の例で group 名が小文字始まり（`"meta".group`）
- file: skills/katachi/references/setup/step3/3-2-write-the-definition.md
- line: 72
- quote: "meta".group {
- impact: あり: 要確認。agent が写すと小文字の group 名が定義に入る
- recommend: 直してもらう 6, 直さない 4

**ルールに反する点**: B29（group 名は大文字で始める）。同じ skill の 3-3 が「Directory names are the group name with a lowercase first letter」と、大文字始まりを前提に書いている。skill に AGENTS の決まりが及ぶかは E13（ルール側が曖昧なので要確認）

**直し方の案**: `"Meta".group {` にする

## [p2] 4-2 の baseline の例だけ `@OptIn` を `val` に付ける形で、3-2 の `@file:OptIn` と違う
- file: skills/katachi/references/setup/step4/4-2-handle-errors.md
- line: 15
- quote: put `@OptIn(ExperimentalKatachiApi::class)` on `val projectArchitecture`
- impact: あり: 要確認。写すと import が無くコンパイルエラーになる恐れ（`@file:OptIn` が既にあれば起きない）
- recommend: 直してもらう 6, 直さない 4

**ルールに反する点**: D2-1 / C15 の趣旨（ファイル全体は `@file:OptIn(ExperimentalKatachiApi::class)` と `import me.tbsten.katachi.ExperimentalKatachiApi` をそろえる。3-2 L20〜24 はその形）。`baseline()` は `@ExperimentalKatachiApi`（`ArchitectureScope.kt` L73）なので両方動くが、skill の中で形が 2 つになる。`import` を足すことも書いていない

**直し方の案**: 「`ProjectArchitecture.kt` は 3-2 の `@file:OptIn` で始まっているはずなので、そのまま `baseline()` を書く」と書く。無ければ「3-2 の experimental APIs の 2 行を足す」と案内する

## [p1] 比べる節が、片方のツールの説明から始まっている
- file: ja/get-started/comparison-with-other-tools.mdx
- line: 41
- quote: Konsist は `Konsist.scopeFromProject()` で対象を絞り、絞った範囲のクラスや関数を検証します。
- impact: なし: 意味は変わらない作法。要確認
- recommend: 直さない 6, 解決済みにする 3

**ルールに反する点**: D3-4・D3-12（比べる節は最初に軸を言ってから片方ずつ説明する）。節の見出しが「katachi と Konsist」で、最初の一文は Konsist だけの説明。軸は L44〜45 の「配置と中身」で出る。L197 の「katachi と detekt」も同じ形

**直し方の案**: 最初に「katachi は配置、Konsist は絞った範囲の中身を検証します。」と軸を言う

## [p1] AI が書いた文に出やすい言い方（非生物主語・比喩）
- file: ja/get-started/comparison-with-other-tools.mdx
- line: 146
- quote: 層の漏れを中身の側から止めます。
- impact: なし: 判断が割れる文体。要確認
- recommend: 直さない 7, 解決済みにする 3

**ルールに反する点**: D3-15。「止めます」は比喩的な動詞で、非生物主語。L174「シリアライズ対象の取りこぼしを防ぎます。」も同じ形

**直し方の案**: 「ViewModel から Android の View 型を参照すると違反になるので、層をまたぐ依存を検出できます。」

## [p1] 比べる節が、片方のツールの説明から始まっている（detekt）
- file: ja/get-started/comparison-with-other-tools.mdx
- line: 197
- quote: detekt は Kotlin のソースを静的解析して、コードスメルやスタイルの逸脱を指摘します。
- impact: なし: 意味は変わらない作法。要確認
- recommend: 直さない 6, 解決済みにする 3

**ルールに反する点**: D3-4・D3-12。L41 と同じ形

**直し方の案**: 「katachi は配置、detekt はファイルの中身の書き方を見ます。」を先頭に足す

## [p1] 「これは」が前の文を指している
- file: ja/get-started/faq.mdx
- line: 36
- quote: これは **fail-safe defaults**（デフォルトの振る舞いを安全なほう、つまり許可しないほうにしておく）と呼ばれる設計原則の1つです。
- impact: なし: 意味は変わらない作法
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: D3-3。「想定外のものをまず疑う考え方」を指す（理由の説明ではなく名前の紹介なので、decisions の「これは」の許容とは別）

**直し方の案**: 「想定外のものをまず疑う考え方は **fail-safe defaults** …と呼ばれる設計原則の1つです。」と 1 文にする

## [p1] 「これは」が前の段落を指している
- file: ja/get-started/faq.mdx
- line: 67
- quote: これはそのまま、**チームがアーキテクチャについて合意し直す作業**でもあります。
- impact: なし: 意味は変わらない作法
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: D3-3

**直し方の案**: 「違反を1つずつ片付けることは、そのまま…」

## [p1] ProjectArchitecture.kt に group と Role の定義を直接書いている
- file: ja/get-started/first-architecture.mdx
- line: 178
- quote: "Domain".group {
- impact: なし: 書き手向けの決まり（skill）と docs の例の食い違い。ルールの側の曖昧さ
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（`ProjectArchitecture.kt` は group の関数を呼ぶだけ）。ただし B20 は skill の決まりで、ガイドは書いていない（E13 と同じく、skill の決まりが docs に及ぶか不明）。最初の 1 本の例としては、1 ファイルのほうが写しやすい

**直し方の案**: 直さない。ガイドに決まりを書くなら、この例は `"Domain".group` の中身を別ファイルにする必要がある

## [p1] 「これが」が前の文を指している
- file: ja/get-started/first-architecture.mdx
- line: 222
- quote: katachi は許可リスト方式なので、これが正常な出発点です。
- impact: なし: 意味は変わらない作法
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: D3-3。「宣言していないファイルやディレクトリが違反として出る」ことを指す

**直し方の案**: 「katachi は許可リスト方式なので、宣言していないものが違反として出るのが正常な出発点です。」

## [p1] 「ガードレールとして機能する」は比喩
- file: ja/get-started/first-architecture.mdx
- line: 237
- quote: katachi がガードレールとして機能するか検証させましょう。
- impact: あり: 公開される手順の文。要確認
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: D3-7（挙動を比喩で言わず、何が起きるかを書く）。motivation L34 の「通さないガードレール」は decisions で「直さない」だが、これは別のファイルで、「通さない」を含まない。同じ趣旨なので判断に寄せる

**直し方の案**: 「katachi が違反を検出するか検証させましょう。」

## [p1] 「これを」が指すものを書いていない
- file: ja/get-started/motivation.mdx
- line: 79
- quote: これを宣言的な DSL で書くと、プロジェクト全体のディレクトリ構造をテストできるようになります。
- impact: なし: 意味は変わらない作法
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: D3-3（指示語ではなく指すものを書く）。直前の箇条書きの 2 項目（Role と配置）を指す

**直し方の案**: 「Role と配置を宣言的な DSL で書くと、…」

## [p1] 「このルール」が指すものが曖昧
- file: ja/get-started/motivation.mdx
- line: 89
- quote: このルールによって検知される例
- impact: なし: 意味は変わらない作法
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: D3-3。「ルール」は直前では `projectArchitecture` の宣言だが、その節では「ルール」と呼んでいない

**直し方の案**: 「この定義で検知される例」

## [p1] group を architecture の中に直接書いている（baseline の設定）
- file: ja/guides/baseline.mdx
- line: 58
- quote: "Tool".group {
- impact: あり: 同上（要確認）
- recommend: 直さない 8, 直してもらう 2

**ルールに反する点**: B20（同上）。この例は `// ...` で省いた断片で、`baselineFile()` の関数は同じブロックの下に書いてある

**直し方の案**: 直さない（断片の例）

## [p1] Baseline ファイルの Role が生成ドキュメントに出る形のまま（設定の手順）
- file: ja/guides/baseline.mdx
- line: 67
- quote: "BaselineFile".role {
- impact: あり: 同上（要確認）
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: B30（ツールが書く単独のファイルの Role）

**直し方の案**: `documented = false` を足す

## [p1] Baseline ファイルの Role が生成ドキュメントに出る形のまま（まとめ）
- file: ja/guides/baseline.mdx
- line: 113
- quote: "BaselineFile".role {
- impact: あり: 同上（要確認）
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: B30

**直し方の案**: 上と同じ（67 行目と揃える）

## [p1] 非生物主語で「明確になります」（group の説明）
- file: ja/guides/basic-api.mdx
- line: 122
- quote: Role のグループをドメインレイヤーとして DSL で表すと
- impact: あり: 読み手には伝わるが回りくどい（要確認）
- recommend: 解決済みにする 5, 直さない 5

**ルールに反する点**: D3-15（「〜すると、〜が明確になります」と非生物主語で書き、同じ文で「分かりやすい」を 2 回重ねる）

**直し方の案**: 「group でドメインレイヤーを表すと、どの Role が同じ層かが読み取れます。粒度は、読んで分かる大きさにそろえてください。」

## [p1] 別モジュールと書くが、依存の足し方に触れていない（basic-api）
- file: ja/guides/basic-api.mdx
- line: 240
- quote: は別モジュール `katachi-konsist` に入っています
- impact: あり: 写して試した人が依存の足し方を探す（要確認）
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: C1（`katachi-konsist` の依存と `assert(FileConstraintCheck())` をそろえて案内する）。依存の行 `testImplementation("me.tbsten.katachi:katachi-konsist:<version>")` は kt-file-content.mdx L96〜100 にだけあり、このページは直後（L244）の kt-file-content へのリンクに任せている

**直し方の案**: 「依存の足し方は kt-file-content のガイドにあります」と一言添える

## [p1] ファイル名のパターンがコード表記でない（3 か所に分ける。要確認）
- file: ja/guides/cli-and-script/katachi-script.mdx
- line: 81
- quote: *.katachi.kts の実行には katachi CLI が必要です。
- impact: なし: 表記ゆれ（意味は変わらない。要確認）
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: A2（API・識別子はコード表記）。ファイル名のパターンが A2 の対象かは決まりが無い。同じページの L243・L324・L406 は `*.katachi.kts` とバッククォートで書いていて不統一

**直し方の案**: 3 か所とも `*.katachi.kts`・`*.main.kts` とバッククォートで囲む

同じ指摘: ja/guides/cli-and-script/katachi-script.mdx L88, ja/guides/cli-and-script/katachi-script.mdx L117

## [p1] ファイル名のパターンがコード表記でない（3 か所に分ける。要確認）
- file: ja/guides/cli-and-script/katachi-script.mdx
- line: 88
- quote: *.main.kts は標準的な Kotlin script であるため
- impact: なし: 表記ゆれ（意味は変わらない。要確認）
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: A2（API・識別子はコード表記）。ファイル名のパターンが A2 の対象かは決まりが無い。同じページの L243・L324・L406 は `*.katachi.kts` とバッククォートで書いていて不統一

**直し方の案**: 3 か所とも `*.katachi.kts`・`*.main.kts` とバッククォートで囲む

同じ指摘: ja/guides/cli-and-script/katachi-script.mdx L81, ja/guides/cli-and-script/katachi-script.mdx L117

## [p1] ファイル名のパターンがコード表記でない（3 か所に分ける。要確認）
- file: ja/guides/cli-and-script/katachi-script.mdx
- line: 117
- quote: *.katachi.kts ではトップレベルの
- impact: なし: 表記ゆれ（意味は変わらない。要確認）
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: A2（API・識別子はコード表記）。ファイル名のパターンが A2 の対象かは決まりが無い。同じページの L243・L324・L406 は `*.katachi.kts` とバッククォートで書いていて不統一

**直し方の案**: 3 か所とも `*.katachi.kts`・`*.main.kts` とバッククォートで囲む

同じ指摘: ja/guides/cli-and-script/katachi-script.mdx L81, ja/guides/cli-and-script/katachi-script.mdx L88

## [p1] 1 文目が崩れている
- file: ja/guides/diff-report.mdx
- line: 12
- quote: Pull request にその Pull request でどの Role が変わったのか report が表示されると便利でしょう。
- impact: あり: 意味は伝わるが読みにくい（要確認）
- recommend: 解決済みにする 6, 直さない 4

**ルールに反する点**: D3-15（読みやすさ。「Pull request に…その Pull request で」と重なり、「report」だけ小文字の英語）

**直し方の案**: 「Pull request に、その Pull request で変わった Role の一覧がレポートとして表示されると便利です。」

## [p1] 抽象的な効果の文
- file: ja/guides/diff-report.mdx
- line: 43
- quote: レビュー体験を向上させることができます
- impact: あり: 読み手に効果が伝わらない（要確認）
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: D3-15（何が起きるかを書く。D3-7 と同じ方向）。根拠: 何が良くなるのか書いていない

**直し方の案**: 「CI やレビュー補助の Agent Skill で `katachiDiff` を実行すると、レビューする人が最初に変更された Role の一覧を読めます。」

## [p1] 「このように」が指すものが次の画像
- file: ja/guides/diff-report.mdx
- line: 90
- quote: 重みの有無で Importance 列の見え方がこのように変わります。
- impact: あり: 読み手が画像を見るまで分からない（要確認）
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: D3-3（指示語ではなく指すものを書く）。根拠: 画像を見ないと何が変わるか分からない。decisions の指示語 6 件とは別の箇所

**直し方の案**: 「重みの有無で Importance 列の色の濃さが変わります（下の画像は同じ差分を重みなしと `diffWeight = 10.0` で比べた例です）。」

## [p1] 「markdown」「Markdown」「マークダウン」の表記ゆれ
- file: ja/guides/document-generation.mdx
- line: 15
- quote: katachi は `architecture { }` 定義から markdown ドキュメントを生成できます。
- impact: なし: 表記ゆれ
- recommend: 直さない 5, 直してもらう 5

**ルールに反する点**: 表記ゆれ（L217 は「Markdown」、タブのラベルと L292・L529 は「マークダウン」）。prh.yml・AGENTS に決まりは無い

**直し方の案**: 「Markdown」にそろえる（ラベルも）。決まりを AGENTS に足すかは判断

## [p1] 指示語「こちら」
- file: ja/guides/document-generation.mdx
- line: 155
- quote: こちらも参考にしてください。
- impact: あり: 軽微
- recommend: 直さない 5, 直してもらう 5

**ルールに反する点**: D3-3（指すものを書く）。節の頭が LinkCard の直前なので、指すのは `sample/` の生成物

**直し方の案**: 「`sample/` の生成物も参考にしてください。」

## [p1] 句点のあとに半角スペース
- file: ja/guides/document-generation.mdx
- line: 279
- quote: 出なくなります。 中の Role が自分で何と書いていても同じです。
- impact: なし: 表記ゆれ（表示上ほぼ変わらない）
- recommend: 解決済みにする 6, 直してもらう 4

**ルールに反する点**: 表記ゆれ（和文の句点のあとにスペース。ほかのページには無い）。D3 の方針ではなく作法

**直し方の案**: スペースを削る

## [p1] 句点のあとに半角スペース（2 か所目）
- file: ja/guides/document-generation.mdx
- line: 405
- quote: 深さで決まります。 最上位が
- impact: なし: 表記ゆれ
- recommend: 解決済みにする 6, 直してもらう 4

**ルールに反する点**: 同上

**直し方の案**: スペースを削る

## [p1] コード例で分かる設定を本文で説明し直している
- file: ja/guides/generate-code-from-template.mdx
- line: 165
- quote: モジュールのディレクトリとパッケージのディレクトリには、同じ名前 `feature` の capture を書いています。
- impact: なし: 意味の変わらない作法。内容の誤りは無い
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: D3-13（コード例で分かる設定を、本文で 1 行ずつ説明し直さない。理由はコメント 1 行で足りる）。ただし後半の「同じ名前の capture は同じ値になる」は L137 の例を読んだだけでは分からない

**直し方の案**: 前半を落とし、「同じ名前の capture は同じ値になるので、`--arg feature=home` の 1 つで両方の `*` が決まります」から始める。コード側に `// 同じ名前なら同じ値` のコメントを足してもよい

## [p1] README を作る Role に `documented = false` が無い
- file: ja/guides/generate-code-from-template.mdx
- line: 174
- quote: "FeatureReadme".role {
- impact: あり: 要確認。写した利用者の生成ドキュメントに README の Role が並ぶ
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: B30（生成ドキュメントで読まれないファイル、単独の README などの Role には `documented = false` を付ける。推奨）

**直し方の案**: `documented = false` を 1 行足す。載せたいなら直さない

## [p1] README を作る Role に `documented = false` が無い（まとめの例・要確認）
- file: ja/guides/generate-code-from-template.mdx
- line: 249
- quote: "FeatureReadme".role {
- impact: あり: 要確認。同上
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: B30

**直し方の案**: L174 と同じ

## [p1] 「宣言して良い宣言」と読みにくい
- file: ja/guides/kt-file-content.mdx
- line: 138
- quote: に 宣言して良い宣言の種類と名前を渡して定義します。
- impact: あり: 要確認。意味は通じる。読み手のつまずき程度
- recommend: 直してもらう 5, 直さない 5, 解決済みにする 6

**ルールに反する点**: D3-15（読みやすさ。「宣言して良い宣言」が重なる）。全角文字の前に半角スペースも入っている

**直し方の案**: 「許可したい宣言の種類と名前を渡します。」

## [p1] 2 行だけの表「type 宣言の詳細を指定する」
- file: ja/guides/kt-file-content.mdx
- line: 232
- quote: <td>やりたいこと</td>
- impact: なし: 意味の変わらない作法
- recommend: 直さない 8, 直してもらう 2

**ルールに反する点**: D3-11（表にするのは複数のものを複数の観点で比べるときだけ）。「やりたいこと」→「例」の 2 行 × 1 観点

**直し方の案**: 見出し付きの 2 つのコード例にする

## [p1] 「戻り値を指定」の行の「補足」が空
- file: ja/guides/kt-file-content.mdx
- line: 375
- quote: <td>
- impact: あり: 要確認。画面に空のセルが出る
- recommend: 直してもらう 5, 直さない 5

**ルールに反する点**: D3-11（表は複数の観点で比べるときだけ。ここは補足の列が 1 行分空で、もう 1 行だけの表）。L337 の「関数の引数・戻り値の定義」の表の、2 行目の補足セル（L375〜L377）

**直し方の案**: 補足が要らないなら列を外す、または「戻り値の型を `.returns("…")` で指定します」と 1 行足す

## [p1] 「修飾子」と「modifier」が混ざっている
- file: ja/guides/kt-file-content.mdx
- line: 394
- quote: `annotated()` を modifier と同じように指定します。
- impact: あり: 表記ゆれ。誤解は生まない
- recommend: 解決済みにする 7, 直してもらう 4

**ルールに反する点**: B7（同じ概念を、同じページ・続くページで別の語で呼ばない）。根拠: 同ページ L213 と kotlin-mapping の節の名前は「修飾子」

**直し方の案**: 「`annotated()` を修飾子と同じように指定します。」

## [p1] 太字が多い
- file: ja/guides/kt-file-content/kotlin-mapping.mdx
- line: 22
- quote: **違反** … 書いていない宣言は違反になります
- impact: あり: 要確認。読み心地だけの話
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: D3-15（過剰な太字）。**違反**・**問わない**・**問いません** が各節の導入文に繰り返し出る（L22・L23・L27・L214・L294・L395・L465・L543）

**直し方の案**: 太字は L22・L23 の用語の定義だけにし、各節の導入文は地の文にする

## [p1] primary constructor の行のコメントが分かりにくい
- file: ja/guides/kt-file-content/kotlin-mapping.mdx
- line: 198
- quote: // val を書かない引数はプロパティでない引数
- impact: あり: 要確認。読み手が迷う
- recommend: 直してもらう 6, 直さない 3

**ルールに反する点**: D3-15（読みやすさ）。直後の行は `.primaryConstructor("val value: String")` と `val` を書いており、コメントが何の説明かが分からない

**直し方の案**: 「`val` を書いた引数は、`allow(val, …)` の行も要る（プロパティになるため）」など、書いてある行について言う

## [p1] 表の「noinline」の例が、左の Kotlin の書き方に無い関数を指している
- file: ja/guides/kt-file-content/kotlin-mapping.mdx
- line: 284
- quote: allow(`fun`, "run")("noinline block: () -> Unit")
- impact: あり: 公開ページの表。対応しない行が 1 つ出る
- recommend: 直してもらう 6, 直さない 3

**ルールに反する点**: D3-15 に近い読み手の迷い（左の Kotlin 例は `fun log(vararg args: Any)` と `fun save(force: Boolean = false)` の 2 つだけで、`run` と `noinline` の例が無い。見出しの「`noinline` / `crossinline`」と対応しない）

**直し方の案**: 左に `inline fun run(noinline block: () -> Unit) {}` を足す

## [p1] IDE 設定の Role が生成ドキュメントに出る形のまま
- file: ja/guides/layout.mdx
- line: 154
- quote: "IdeCodeStyle".role {
- impact: あり: 同上（要確認）
- recommend: 直さない 8, 直してもらう 2

**ルールに反する点**: B30（ツールの設定ファイルの Role）

**直し方の案**: `documented = false` を足すか、直さない

## [p1] ファイルの種類が生成ドキュメントに出る形のまま（GeneratedFile）
- file: ja/guides/layout.mdx
- line: 183
- quote: "GeneratedFile".role {
- impact: あり: 写した定義が生成物をドキュメントに載せる（要確認）
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: B30（生成物の Role には `documented = false` を付ける。推奨）

**直し方の案**: `documented = false` を 1 行足す。または「生成ドキュメントに出さないときは `documented = false`」とコメントを添える

## [p1] ファイルの種類が生成ドキュメントに出る形のまま（BuildOutput）
- file: ja/guides/layout.mdx
- line: 190
- quote: "BuildOutput".role {
- impact: あり: 同上（要確認）
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: B30

**直し方の案**: 上と同じ

## [p1] 指示語「いずれも」
- file: ja/guides/processor.mdx
- line: 112
- quote: いずれもプロジェクトのアーキテクチャ定義を受け取り、検証や生成に使っています。
- impact: あり: 軽微
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: D3-3（指すものを書く）。直前の表を指す。削る案は L63〜64 の件と同じ

**直し方の案**: 「6 つの processor はいずれも」と書くか、削る

## [p1] 型名・メソッド名がコード表記でない
- file: ja/guides/processor.mdx
- line: 194
- quote: ArchitectureProcessorNoArg か ArchitectureProcessor を実装したクラスを用意し、process メソッドを実装してください。
- impact: あり: 軽微（表記）
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: A2（API はコード表記）。L33 は `process` と書いているので同じページ内でも揺れている。ただし decisions で「全部をコード表記にしなくてよい」という利用者の考えがあり、概念名としての ArchitectureProcessor は他の箇所も素のまま

**直し方の案**: `ArchitectureProcessorNoArg`・`ArchitectureProcessor`・`process` に

## [p1] processor の例が断片（import・opt-in 無し、省略の断りも無い）
- file: ja/guides/processor.mdx
- line: 206
- quote: object CountFilesPerRole : ArchitectureProcessorNoArg<Map<String, Int>> {
- impact: あり: 軽微。要確認
- recommend: 直さない 8, 直してもらう 2

**ルールに反する点**: D2-2。同じ指摘が L234（quote: `object CountFilesPerRole : ArchitectureProcessor<CountFilesPerRole.Args, Map<String, Int>> {`）と L263（quote: `// metadata のキー`）にもある（場所ごとに別件）

**直し方の案**: `// package / import の行は省略` を各ブロックの先頭に足す。断片は書かない決まり（D2-1）なので、直さない判断もありえる

## [p1] 同上（引数ありの例）
- file: ja/guides/processor.mdx
- line: 236
- quote: object CountFilesPerRole : ArchitectureProcessor<CountFilesPerRole.Args, Map<String, Int>> {
- impact: あり: 軽微。要確認
- recommend: 直さない 8, 直してもらう 2

**ルールに反する点**: D2-2（L204 と同じ）

**直し方の案**: L204 と同じ

## [p1] 同上（Role の metadata の例）
- file: ja/guides/processor.mdx
- line: 264
- quote: // metadata のキー
- impact: あり: 軽微。要確認
- recommend: 直さない 8, 直してもらう 2

**ルールに反する点**: D2-2（L204 と同じ）。この例は `MetadataKey`・`metadata()` の import も要る

**直し方の案**: L204 と同じ

## [p1] 「それぞれを」が何を指すか書いていない
- file: ja/guides/role.mdx
- line: 74
- quote: それぞれを、もう少し詳しく説明します。
- impact: あり: 読み手が指すものを一瞬迷う（要確認）
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: D3-3（指示語ではなく指すものを書く）。「それぞれ」は直前の 1 つの例の title / summary / layout / ktFileContent を指す

**直し方の案**: 「title・summary・layout・ktFileContent を、1 つずつ詳しく説明します。」

## [p1] 別モジュールと書くが、依存の足し方に触れていない（role）
- file: ja/guides/role.mdx
- line: 227
- quote: は別モジュール `katachi-konsist` に入っています
- impact: あり: 同上（要確認）
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: C1（上と同じ）。L209 に kt-file-content へのリンクはあるが、この段落とは離れている

**直し方の案**: 上と同じ

## [p1] 非生物主語「〜が分かれてしまいます」（Role を分ける理由）
- file: ja/guides/role.mdx
- line: 270
- quote: 説明・制約・テンプレートが「どちらのファイルの話か」で分かれてしまいます
- impact: あり: 読み手には伝わる（要確認）
- recommend: 直さない 6, 解決済みにする 4

**ルールに反する点**: D3-15（非生物主語）

**直し方の案**: 「1つの Role にまとめると、説明・制約・テンプレートのそれぞれで『どちらのファイルの話か』を書き分けることになります。」

## [p1] Role の関数を group なしで ProjectArchitecture.kt から呼んでいる
- file: ja/guides/role.mdx
- line: 379
- quote: useCase()
- impact: あり: 写すと skill の命名と違う形になる（要確認）
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: B20（`ProjectArchitecture.kt` は group の関数を呼ぶだけ。Role のファイルは `<group>/<Role>Role.kt`、group の外は直下）。分割後の例は `domain/` の下に Role のファイルを置くのに、`Domain` group が無い

**直し方の案**: 分割後の例も `"Domain".group { useCase() ... }` にする。または直さない

## [p1] group を ProjectArchitecture.kt の中に直接書いている（role のまとめ）
- file: ja/guides/role.mdx
- line: 497
- quote: "Domain".group {
- impact: あり: 同上（要確認）
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt` の関数にして、`ProjectArchitecture.kt` はその関数を呼ぶだけ）

**直し方の案**: `domain/DomainGroup.kt` を足す。または直さない

## [p1] ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- file: ja/recipes/ai-agent.mdx
- line: 35
- quote: title=roles/ClaudeCode.kt
- impact: なし: ガイドに書かれていない skill 側の命名で、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる

同じ指摘: ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## [p1] ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- file: ja/recipes/ai-agent.mdx
- line: 130
- quote: title=roles/Cursor.kt
- impact: なし: ガイドに書かれていない skill 側の命名で、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる

同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## [p1] ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- file: ja/recipes/ai-agent.mdx
- line: 233
- quote: title=roles/Windsurf.kt
- impact: なし: ガイドに書かれていない skill 側の命名で、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる

同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## [p1] ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- file: ja/recipes/ai-agent.mdx
- line: 328
- quote: title=roles/GitHubCopilot.kt
- impact: なし: ガイドに書かれていない skill 側の命名で、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる

同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## [p1] ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- file: ja/recipes/ai-agent.mdx
- line: 416
- quote: title=roles/Cline.kt
- impact: なし: ガイドに書かれていない skill 側の命名で、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる

同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## [p1] ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- file: ja/recipes/ai-agent.mdx
- line: 487
- quote: title=roles/Junie.kt
- impact: なし: ガイドに書かれていない skill 側の命名で、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる

同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## [p1] ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- file: ja/recipes/ai-agent.mdx
- line: 567
- quote: title=roles/Codex.kt
- impact: なし: ガイドに書かれていない skill 側の命名で、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる

同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L636, ja/recipes/ai-agent.mdx L707

## [p1] ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- file: ja/recipes/ai-agent.mdx
- line: 636
- quote: title=roles/Antigravity.kt
- impact: なし: ガイドに書かれていない skill 側の命名で、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる

同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L707

## [p1] ai-agent: group 関数と Role の定義ファイルの名前が B20 の形でない
- file: ja/recipes/ai-agent.mdx
- line: 707
- quote: title=roles/Aider.kt
- impact: なし: ガイドに書かれていない skill 側の命名で、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）。group の関数と Role 8 個ほどを `roles/ClaudeCode.kt` の 1 ファイルに入れている。B20 はガイドが決まりとして書いておらず skill の決まり（E13 と同じ「skill の決まりが docs に及ぶか」）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `claudeCode/ClaudeCodeGroup.kt` と Role ごとのファイルに分けるが、レシピが長くなる

同じ指摘: ja/recipes/ai-agent.mdx L35, ja/recipes/ai-agent.mdx L130, ja/recipes/ai-agent.mdx L233, ja/recipes/ai-agent.mdx L328, ja/recipes/ai-agent.mdx L416, ja/recipes/ai-agent.mdx L487, ja/recipes/ai-agent.mdx L567, ja/recipes/ai-agent.mdx L636

## [p1] android-three-layer: Role の定義ファイル名が B20 の形でない
- file: ja/recipes/android-three-layer.mdx
- line: 117
- quote: title=roles/UiState.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする

同じ指摘: ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## [p1] android-three-layer: Role の定義ファイル名が B20 の形でない
- file: ja/recipes/android-three-layer.mdx
- line: 148
- quote: title=roles/ViewModel.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする

同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## [p1] android-three-layer: Role の定義ファイル名が B20 の形でない
- file: ja/recipes/android-three-layer.mdx
- line: 181
- quote: title=roles/Screen.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする

同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## [p1] android-three-layer: Role の定義ファイル名が B20 の形でない
- file: ja/recipes/android-three-layer.mdx
- line: 211
- quote: title=roles/UseCase.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする

同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## [p1] android-three-layer: Role の定義ファイル名が B20 の形でない
- file: ja/recipes/android-three-layer.mdx
- line: 243
- quote: title=roles/DomainModel.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする

同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## [p1] android-three-layer: Role の定義ファイル名が B20 の形でない
- file: ja/recipes/android-three-layer.mdx
- line: 263
- quote: title=roles/RepositoryInterface.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする

同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L294, ja/recipes/android-three-layer.mdx L325

## [p1] android-three-layer: Role の定義ファイル名が B20 の形でない
- file: ja/recipes/android-three-layer.mdx
- line: 294
- quote: title=roles/RepositoryImplementation.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする

同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L325

## [p1] android-three-layer: Role の定義ファイル名が B20 の形でない
- file: ja/recipes/android-three-layer.mdx
- line: 325
- quote: title=roles/DataSource.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（Role は `<group>/<Role>Role.kt`。ここは `roles/UiState.kt` で、group のディレクトリも `Role` の語も無い）。3 層の group（`ui/`・`domain/`・`data/`）と対応しない置き方

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ui/UiStateRole.kt` のように group ごとのディレクトリにする

同じ指摘: ja/recipes/android-three-layer.mdx L117, ja/recipes/android-three-layer.mdx L148, ja/recipes/android-three-layer.mdx L181, ja/recipes/android-three-layer.mdx L211, ja/recipes/android-three-layer.mdx L243, ja/recipes/android-three-layer.mdx L263, ja/recipes/android-three-layer.mdx L294

## [p1] detekt: group と 2 つの Role を 1 つの `roles/Detekt.kt` に入れている（B20・要確認）
- file: ja/recipes/detekt.mdx
- line: 40
- quote: // roles/Detekt.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `detekt/DetektGroup.kt`

## [p1] git: group と 3 つの Role を 1 つの `roles/GitRole.kt` に入れている（B20・要確認）
- file: ja/recipes/git.mdx
- line: 31
- quote: title=roles/GitRole.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、Role は `<Role>Role.kt`、1 ファイルに group か Role を 1 つ）。ファイル名は Role 用の `…Role.kt` の形だが、中身は group の関数

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `git/GitGroup.kt` にする

## [p1] github: group と 4 つの Role を 1 つの `GitHub.kt` に入れている（B20・要確認）
- file: ja/recipes/github.mdx
- line: 31
- quote: title=GitHub.kt nocollapse
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `github/GitHubGroup.kt` にする

## [p1] ktlint: group と 2 つの Role を 1 つの `roles/Ktlint.kt` に入れている（B20・要確認）
- file: ja/recipes/ktlint.mdx
- line: 32
- quote: // roles/Ktlint.kt
- impact: なし: 命名だけで、動作にも影響しない（要確認）
- recommend: 直さない 8, 直してもらう 1

**ルールに反する点**: B20（group は `<group>/<Group>Group.kt`、1 ファイルに group か Role を 1 つ）

**直し方の案**: 直さない（ガイドに決まりが無い）。そろえるなら `ktlint/KtlintGroup.kt`

## [p1] 現在地の文が読みにくい
- file: ja/roadmap.mdx
- line: 14
- quote: katachi は現在 当初設計された必要となるであろう機能の大部分の実装を終え、
- impact: あり: 公開されるページの本文。要確認
- recommend: 解決済みにする 5, 直さない 4, 直してもらう 3

**ルールに反する点**: D3-15（`yomiyasu` での推敲）。L16「katachi にはまだその点で非常に未熟であり」は主語と述語がずれ、「ツールは…成長します」は非生物主語

**直し方の案**: 「katachi は、当初の設計で必要と考えた機能のほとんどを実装し終え、…」

## [p1] CSS コメントに「既定」（D4-1）
- file: skills/katachi/assets/ja/check-list.html
- line: 1117
- quote: /* 背景グラフィックを出さない既定の印刷でも読めるよう、塗りではなく枠線で示す */
- impact: なし: CSS コメントで画面に出ない
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: D4-1（「既定」→「デフォルト」）。`lint:text` は assets を見ないので目で拾った

**直し方の案**: 「背景グラフィックを出さないデフォルトの印刷でも読めるよう、…」

## [p1] 記入例の選択肢に「ignore」がコード表記でない・理由も選択肢に無い（A2・B11）
- file: skills/katachi/assets/ja/check-list.html
- line: 1487
- quote: "当面 ignore する"
- impact: なし: HTML コメント内の記入例で画面に出ない（エージェントが形を写すだけ）
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: A2（API はコード表記）・B11（`ignore()` は逃げ道。何のためかを添える）。選択肢の直後の `recommendation` に「対象が1画面だけで、移行の予定が決まっていないため」と理由があるので B11 は満たす。A2 だけが残る（JSON の文字列なのでバッククォートは書きにくい）

**直し方の案**: 「当面 `ignore()` で外す」、または API 名を出さず「当面は検査の対象から外す」

## [p1] CSS コメントに「既定」（D4-1）
- file: skills/katachi/assets/ja/project-code-base-report.html
- line: 970
- quote: /* メタ情報（既定で閉じる） */
- impact: なし: CSS コメントで画面に出ない
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: D4-1（「既定」→「デフォルト」）。`lint:text` は assets を見ないので目で拾った

**直し方の案**: 「メタ情報（デフォルトで閉じる）」

## [p1] 記入例の Role `AppBuildFile` が build.gradle.kts を手書きの Role にして見える（B16・要確認）
- file: skills/katachi/assets/ja/project-code-base-report.html
- line: 1250
- quote: "name": "AppBuildFile",
- impact: なし: HTML コメント内の記入例（要確認）
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: B16（Gradle のファイルは `gradle()` の 1 行で宣言し、手書きの Role にしない。中身の制約は `gradle { buildScript { fileConstraint(…) } }` のフックに書く）。ただし `constraintOnly: true` の Role は UI 文言 L1555 が「gradle() のファイルに検査を足す。ドキュメントには載せない」と説明するので、「制約をかけるための候補」の書き方としては合っている。note の「Role」が手書きの Role に読めるだけ

**直し方の案**: note を「namespace とアプリのパッケージ名が一致することを、`gradle()` のフックで検査する（Role は作らない）」のように書く

## [p1] 記入例の選択肢に「ignore」がコード表記でない（A2）
- file: skills/katachi/assets/ja/project-code-base-report.html
- line: 1286
- quote: "当面 ignore する"
- impact: なし: HTML コメント内の記入例
- recommend: 直さない 7, 直してもらう 3

**ルールに反する点**: A2。B11 の理由は `recommendation`（「対象が1画面だけで、移行の予定が決まっていないため」）にある

**直し方の案**: 「当面 `ignore()` で外す」または「当面は検査の対象から外す」

## [p1] 記入例で Role 名と実ファイル名の呼び方が違う（`UseCaseImplementation` と `UseCaseImpl`）（B7・要確認）
- file: skills/katachi/assets/ja/project-code-base-report.html
- line: 1302
- quote: "role": "UseCaseImplementation",
- impact: なし: HTML コメント内の記入例
- recommend: 直さない 8, 直してもらう 2

**ルールに反する点**: B7（同じ種類のファイルを別の語で呼ばない）。B7 は「Role 名を `UseCaseImplementation` と `UseCaseImpl` で揺らす」ことを指す。この例は Role 名は `UseCaseImplementation` にそろい、ファイル名の接尾辞だけが `Impl` なので、違反でなく名前の付け方の実例とも読める

**直し方の案**: 直さなくてよい。揃えるなら Role を `UseCaseImpl` に

## [p1] add-a-role の Worker の例に省略の断りが無い
- file: skills/katachi/references/add-and-update/add-a-role.md
- line: 36
- quote: "Worker".role {
- impact: あり: 写して import を足し忘れてもコンパイルエラーから直せる（要確認）
- recommend: 直さない 8, 直してもらう 2

**ルールに反する点**: D2-2（断片の例で package / import を省くなら、省いたと書く）

**直し方の案**: 例の前に「(imports omitted)」と一言添える。add-and-update は既存の定義へ足す作業なので、ファイルの先頭の import は利用者のファイルにすでにある、で足りるとも読める（要確認）

## [p1] group 名の例 `feature.ui` が小文字始まり
- file: skills/katachi/references/setup/step1/1-2-fill-the-report.md
- line: 22
- quote: feature.ui
- impact: あり: 要確認。レポートの `group` に小文字の group 名を書く見本になる
- recommend: 直してもらう 4, 直さない 6

**ルールに反する点**: B29（group 名は大文字で始める）。E13（skill に及ぶか未定）。レポートの JSON の例で、定義のコードではない

**直し方の案**: `Feature.Ui` のように大文字始まりにする。E13 で「skill には及ばない」と決めるなら直さない

## [p1] コメントの「role」が小文字（✗ の側）
- file: skills/katachi/references/setup/step3/3-2-write-the-definition.md
- line: 62
- quote: // ✗ Different kinds of files packed into one role
- impact: なし: 表記ゆれ（コード例のコメントだけ）
- recommend: 直してもらう 5, 直さない 5

**ルールに反する点**: A1 の英語版（概念は「Role」と大文字。skill の本文は全部 `Role`）。出力の写し（2-2 の `Add a new role for it`）は実装どおりなので対象外

**直し方の案**: `one Role`

## [p1] ✗/○ の例が 4 スペースの字下げ
- file: skills/katachi/references/setup/step3/3-2-write-the-definition.md
- line: 63
- quote: "ProjectMeta".role {
- impact: なし: 意味の変わらない作法で、skill への適用範囲が E13 で未定
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: D2-3（Kotlin は 2 スペース）。E13（skill に及ぶか未定）。同じファイルの ViewModel の例（L93〜109）は 2 スペースで、ファイルの中でもそろっていない

**直し方の案**: 2 スペースにする

## [p1] コメントの「role」が小文字（○ の側）
- file: skills/katachi/references/setup/step3/3-2-write-the-definition.md
- line: 71
- quote: // ○ One role per kind, bundled in a group
- impact: なし: 表記ゆれ
- recommend: 直してもらう 5, 直さない 5

**ルールに反する点**: A1 の英語版（上と同じ）

**直し方の案**: `One Role per kind`

## [p1] 6-B の CI の yaml の Role の例に documented = false が無い
- file: skills/katachi/references/setup/step6/6-B-ci.md
- line: 36
- quote: ".github/workflows" / "*.yml".file()
- impact: あり: 書いた Role が生成ドキュメントに載るだけで動作は変わらない（要確認）
- recommend: 直さない 8, 直してもらう 2

**ルールに反する点**: B30（CI の yaml など、ツールの設定の Role には `documented = false` を付ける。推奨）

**直し方の案**: 「(for example …; with `documented = false`)」と添えるか、add-a-role への参照を 1 つ足す。そのままでもよい

## [p1] 6-C の ArchitectureDocs の例に documented = false が無い
- file: skills/katachi/references/setup/step6/6-C-documentation-generation.md
- line: 26
- quote: "ArchitectureDocs".role {
- impact: あり: skill の例を写した利用者が、生成ページの Role が生成ドキュメントに載る形を広める（要確認。推奨の項目で動作は変わらない）
- recommend: 直さない 6, 直してもらう 4

**ルールに反する点**: B30（生成物の Role には `documented = false` を付ける。推奨）。同じ例に D2-2（断片の例で package / import を省いたと書く）も

**直し方の案**: `description = …` の次に `documented = false` を足す。例の前に「package / import の行は省略」と一言添える

## [p3] AI Agent の LinkCard の説明が、消えた katachi agent のサブコマンドを案内している
- file: ja/guides/cli-and-script/cli.mdx
- line: 224
- quote: katachi agent のサブコマンドで、AI Agent に導入を任せるレシピです。
- impact: あり: PR #65 で導入のサブコマンドは消えた。リンク先の ai-agent レシピも AI Agent の設定ファイルを Role にする話で、説明と中身が合わない
- recommend: 直してもらう 7, 解決済みにする 3

手順 4 で見つけたもの。リンク先（recipes/ai-agent）は AI Agent の設定ファイル（.claude/ など）を layout に宣言するレシピ。説明を中身に合わせる（例: 「AI Agent の設定ファイルを Role として宣言するレシピです。」）。英語版（guides/cli-and-script/cli.mdx の同じ LinkCard）も同じ。
