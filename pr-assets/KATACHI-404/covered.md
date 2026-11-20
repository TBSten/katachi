# covered.md — Claude の探索者が見たところ（v0.4.0 prerelease 5 回目、2026-11-20 23:49 JST 時点）

Codex の広域の遊撃に渡す。**ここに無い所を優先する**。1 周目（6 チャーター）と 2 周目の前半（core・katachiDiff・CLI/script・Gradle plugin・Mac の AGP）は終わった。2 周目の後半のうち docs と konsist/konture はクラウドで実行中、実在の OSS への導入と v0.3 からの移行はこれから。9-2（skill の手順書どおりの導入、ja・en）は Mac で実行中。

2 周目の前半の p7 以上 16 件は直す PR を作っている途中（katachiDiff の Role の外への移動・消したファイル・baseline・HTML の `{{DATA}}`、CLI の `agent test` の SKIPPED・写したプロジェクト・読めないチェックリスト・`--module-dir`・buildSrc の `embeddedKotlin`、Gradle plugin の docs の UP-TO-DATE・Groovy DSL・SARIF の上書き・空の outputDir、core の `ignore()` の下の Role・git が使えないとき・断片の名前の重なり）。同じ症状は書かない。

## 1 周目: core

### やったこと
1. `manual-install` だけを頼りに、Ktor サンプルの写し（`proj/`）へ `:architecture-test` を作って最初の `architecture { gradle() ... }` を書いた。最初の 1 回は 5 件の Unexpected が出て、報告の「How to fix」を読めば直せた（期待どおり）
2. 20 通りの layout の誤り（指定漏れ・過剰・重複・`/` の付け方・`..`・`\`・不正な glob・`**`・名前の誤り・拡張子の重複・空の Role・同名の Role など）を 1 回の Gradle で試した（`exp-archive/Experiments.kt`）
3. enum・sealed・data class・value class・fun interface・typealias・companion・init・secondary constructor・拡張関数など 11 種類の `ktFileContent` を、deny by default に当たる形・通る形・報告どおりに直した形で試した（`Exp2.kt`・`Exp5.kt`・`Exp10.kt`）。報告の「Allow it in …」をそのまま貼って通ることも確かめた
4. `ignore()`・`anyFile()`・`wholeTree()`・`gradle()` の二重・`baseline()` の二重・`baseline` の不正な path・`ktFileContent` が 2 つ・`fileConstraint` が例外を投げる／別のファイルを答える、を試した（`Exp3.kt`・`Exp4.kt`・`Exp8.kt`・`Exp9.kt`）
5. baseline を作る→ファイルの改名・削除・追加・宣言の公開範囲を変える→`prune`・`update`・両方同時、と回した。日本語・空白入りのファイル名、CRLF + BOM、タブ、`.kts`・`.json` も混ぜた
6. 配置の違い: モノレポ（git の根の下の `app/`）・`git worktree`・git の無いディレクトリ・外側のリポジトリが無視する場所、で `assert()` が正しい範囲を検査するか見た（261110 の p9 の型）
7. v0.3.0 の `ServiceRole.kt`（`git show v0.3.0:...`）の書き方を 0.4.0 に当て、移行表の「型の位置」「`.module`」「`moduleResolver`」を試した
8. マルチモジュール（`feature/*/api|impl`・`build-logic/`）を `capture("feature")`・`.pascalCase`・`gradle()` で書き、`[CaptureMismatch]` を出させた
9. 設定の誤り: `-Dkatachi.maxViolations`・`-Dkatachi.baseline.update` の不正な値、`architecture = ` の綴り、`katachi { }` 自体を消す
10. 見つけたものを `repro/cases/` に切り出して、コピーして動く形にした

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p6] core: `ignore()` と同じ場所を宣言した Role が黙って効かない（`ktFileContent` は通り、固定名の `file()` は存在するのに MissingFile になる）
- [p6] core: 名前に `[` `{` `$` を含むファイルは、報告の「Add a new role」の断片を貼っても直らない（エスケープすると、存在するファイルが MissingFile になる）
- [p4] core: `ignore()` をファイル名・ファイルの glob に付けても効かず、「No role is defined」と出る
- [p4] core: `-Dkatachi.maxViolations` に不正な値を渡すと、黙って既定（10 件）に戻る
- [p4] core: 外側のリポジトリが無視する場所に置いたプロジェクト（自前の `.git` なし）では、宣言したファイルが全部 MissingFile になる
- [p3] core: import の別名（`as L`）を含む許可パターンは、受け付けるのに一致しない
- [p3] core: layout の書き間違い（`..`・拡張子の重複）は、メッセージが原因を言わない
- [p3] core: 日本語・特殊文字のファイル名の報告が読みにくい・事実と違うことがある
- [p3] core: `fileName` は最初の `.` までで切れ、`Foo.generated.kt` のファイル名が `Foo` になる
- [p3] core: `katachi { architecture = }` の綴りを間違えると、生成されたファイルのコンパイルエラーになる

### 触れなかったところ（ここを優先してよい）
- 日本語の出力（`KATACHI_LANG`）: core の報告は英語だけで、`KATACHI_LANG=ja` でも変わらなかった（言語を切り替えるのは CLI。cli-script のチャーター）。docs に core の日本語出力をうたう記述は見つけていない
- Windows のパス（`\`・ドライブ文字）・大文字小文字を区別しないファイルシステム: クラウドに無い
- Kotlin 2.2 / Gradle 8.0 など、サポートの下限での動作: Kotlin 2.4.10 / Gradle 9.6.0 だけで確かめた
- `katachiTemplate`・`katachiDocs`（docs 生成）・processor の登録・SARIF: Gradle plugin のチャーター側
- `konture`（`katachi-konture`）: konsist-konture のチャーター側
- Android・KMP の `mainSourceSet` / `commonMain`: Android SDK に届かない（環境要因。decisions/environment.md）

## 1 周目: diff

### やったこと
1. 写しを組み、`before=HEAD`（空の差分）で 4 形式が出ることを確認。以降は `before=base after=<ブランチ>` で比べた
2. 5 種（巨大 31 ファイル +2220 行・リネームだけ・バイナリだけ・テストだけ・空）× 4 形式を出し、Markdown を読み、HTML を Playwright で幅 390px / 1400px・ライト / ダークで開いた。横スクロール・コンソールエラー無し
3. 同じ入力（`before=base after=huge`）を数回出して比べた。Markdown・JSON・YAML は 1 分あけても byte 単位で同じ。HTML は「generated 2026-11-20 12:00 UTC」の分の表示だけが違う（下の p2）
4. わざと間違えた引数（存在しない ref・空の `before=`・`weights` の不正値・名前違い・負数・NaN・`maxFiles=abc`・知らない `--arg`・全形式 off・`diffWeight = -1.0` / NaN）。ほとんどのメッセージは直し方まで書いてある
5. git の環境違い: shallow clone（`--depth=1`）・detached HEAD・`origin/main` が無い・monorepo（git のルートの下に `app/`）・ユーザの git 設定（`diff.noprefix`・`diff.renames=false`・`diff.external=/bin/false`・`color.ui=always`・`core.quotepath`）。どれも壊れない
6. 変わった入力: 作業ツリー（ステージ済み・未ステージ・untracked・削除）・モード変更・シンボリックリンク・空ファイル・日本語 / 空白 / `|` / `` ` `` / `<img onerror>` / `;` を含む名前・2500 ファイルの差分
7. guide の例をそのまま貼る: Gradle の `katachi { processors { diff { … } } }`（`maxFiles = 0`・`markdown { outputFile }`・`html { enable = false }`）、`diffWeight`（group / role・`--arg weights=` の優先順位）、`jq` の例。動く
8. HTML の画面操作（コミットの絞り込み・パス絞り込み・warnings only・Copy link）を Playwright で操作
9. `gh` の stub を PATH に置き、`pr=7` の経路（GitHub リンク・コメント組み立て・コメントの差し替え）を手元で再現
10. Actions そのもの（fork の PR の権限不足・`actions/*@vN` の実在）は GitHub 上なのでクラウドでは触れなかった

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p7] diff: 「git diff」のコマンドがシェルの引用符で囲まれず、空白・`;` などのあるパスでは失敗し、最悪は別のコマンドが動く
- [p6] diff: レポートの Markdown が 65,536 字を超えると、PR のコメントが「消えて」ジョブも落ちる
- [p5] diff: untracked のバイナリが、文字のファイルとして行数に数えられ、ヒートが振り切れる
- [p5] diff: リネームのファイルに付く `git diff` のコマンドが、リネームを見せずにファイル全体を「追加」として出す
- [p4] diff: guide の Gradle の例（`markdown { outputFile }`）と action（`diff-report`）を併せると、「diff-report.md が見つからない」で止まる
- [p3] diff: ファイル名に `|` や `` ` `` があると Markdown の表が崩れる
- [p3] diff: monorepo でプロジェクトの外の変更が数えられず、レポートも黙っている
- [p3] diff: PR のコメントに「no role」の警告が 2 回、ファイルが 3 回出る
- [p3] diff: HTML で、`maxFiles` より後ろのファイルをパスで絞り込んでも、行が出ない
- [p2] diff: 空の差分・絞り込み 0 件の HTML が、見出しだけの空の表になる
- [p2] diff: 幅 390px の HTML で、Importance・Diff lines の列が画面の外になる
- [p2] diff: `--arg before=`（空）のエラーが `git rev-parse --verify ^{commit}` だけで、何を指定し直すか分からない
- [p2] diff: HTML だけ、同じ入力でも出力のたびに内容が変わる（`generated … UTC` の時刻）

### 触れなかったところ（ここを優先してよい）
- Actions の実機: fork の PR の権限不足の失敗の仕方・`permissions: contents: read` だけの guide の例で、private リポジトリの `gh pr view` が通るか・`actions/checkout@v7`・`setup-java@v6`・`gradle/actions/setup-gradle@v6`・`upload-artifact@v7`・`download-artifact@v8` の実在（クラウドから api.github.com に届かず 403）。`gh` の stub と action 内のシェル・Python の手元実行までに留めた
- 本リポジトリの過去の PR の差分は使わなかった。`architecture-test` を本リポジトリのまま動かすと katachi 本体のビルドが要り、同時に 6 セッションが走る環境では重いため、`sample/jvm` の写しに同じ型（巨大・リネーム・バイナリ・テスト・空）の場面を作って代えた。本物の差分の「どこが重要か」の目視は、場面が人工的な分だけ弱い
- GitHub 上での Markdown の描画（`|` を含むパス・折りたたみ・コメント長）は実機で見ていない
- shallow clone で `pr=`（`base...head` に merge base が要る経路）の失敗の仕方（`gh` が実在しないと確かめられない）
- IDE プラグイン・katachi CLI（`bin/katachi`）経由は使っていない（チャーターの入口が Gradle の `katachiDiff` のため）

## 1 周目: cli-script

### やったこと
1. `--help` と全サブコマンド（doctor / scaffold / lint / agent test / agent compare-violations / `*.katachi.kts`）を、空のディレクトリで引数を外す・間違える形で 60 通りほど叩いた。終了コードは 0 / 1 / 2 / 3 が出た
2. 最小の Gradle プロジェクト（Kotlin DSL・Groovy・version catalog あり）で `scaffold`（dry-run・本番・2 回目・`--force`・`--module-dir`・`--package`・`--katachi`・`--kotlin`・`--test-framework`・`--deps`）、`agent test`（Gradle 実行 6 回）、`agent compare-violations`、`lint` を通した
3. `*.katachi.kts` の構文エラー・型エラー・存在しない API・実行時例外・`exitProcess`・失敗するコマンド・引数・別ディレクトリのスクリプト・空白入りのパス・シンボリックリンクを試した
4. `KATACHI_WORKDIR`（無い場所・`.`・`/tmp`・空）、check-list.html の `meta.moduleDir` / `meta.projectRoot` の書き換え、作業用ディレクトリが 2 つある場合、`git worktree` からの実行を試した（261110 の KATACHI-353 / 354 は再発していない）
5. `KATACHI_LANG`・ロケール・`kotlin` が PATH に無い・`JAVA_HOME` が不正・出力のリダイレクトを試した
6. 読めないディレクトリがある場所（`nobody` で実行）・`/` からの実行を試した

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p7] cli-script: スクリプトの中のコマンドが失敗しても `katachi <file>.katachi.kts` は終了コード 0 で終わる
- [p7] cli-script: カレントから 4 階層以内に読めないディレクトリがあると、`--version` を含む全コマンドがスタックトレースで落ちる
- [p7] cli-script: `scaffold --katachi` の値を忘れると次のオプションが値として食われ、`--dry-run` が効かずに実際にファイルを書く
- [p6] cli-script: `*.katachi.kts` に渡した `-D…`・`-J…`・`@…` の引数がスクリプトの `args` に届かない
- [p6] cli-script: `scaffold --package` が Kotlin のキーワードを含む名前を通し、生成物がコンパイルできない
- [p6] cli-script: `JAVA_HOME` が存在しない場所でも `doctor` は「そろっています」と言い、Gradle だけが動かない
- [p6] cli-script: `agent test --save` に区切りを含む名前を渡すと、Gradle を最後まで走らせたあとにスタックトレースで終わる（終了コード 3）
- [p4] cli-script: `agent compare-violations` の終了コードと引数の扱いが説明と食い違う
- [p4] cli-script: ルートの build.gradle.kts にだけ `repositories` があるプロジェクトでは、scaffold したモジュールが依存を解決できない
- [p3] cli-script: 細かい文言・表示の引っかかり（まとめて 1 件）

### 触れなかったところ（ここを優先してよい）
- `kotlin` の古い版（2.4.10 しか無い）・日本語 macOS・brew の wrapper（`KATACHI_HOME`）
- Android のモジュール・FAIL_ON_PROJECT_REPOS のプロジェクト・KMP プロジェクトへの scaffold
- `--test-framework kotest-<style>` の全種類のコンパイル（kotest-free の生成だけ確かめた）
- Maven Central が遅いときの `*.main.kts`（`@file:DependsOn`）の待ちと表示
- 色・進捗の表示（出力は端末でもパイプでも ANSI を出さなかった。端末そのものは未確認）
- 同時に 2 つの `agent test` を走らせたときのログのぶつかり（時刻は秒単位）
- doctor で git が無い場合の表示（PATH から git だけを外せなかった）

## 1 周目: gradle-plugin

### やったこと
1. a: Gradle 8.0 + Groovy の settings.gradle / build.gradle（手書き）+ Kotlin 2.3.21。`tasks`・`katachiDocs` を configuration cache（以下 CC）つきで 3 回・`--build-cache` で FROM-CACHE・定義を書き換えて再実行・`mode=check`・`katachiProcessors`・`--arg` の誤り 7 種。CC / build cache とも正しく再実行・再利用された
2. b: Gradle 9.6.0 + build-logic（composite・precompiled script plugin に plugin を入れる）+ 2 モジュールが別の architecture を持つ + java plugin の無いモジュール。`tasks --all`・ルートからの `katachiDocs --continue`・`architecture` と `register` の打ち間違い 13 種・`docs { outputDir = rootProject.layout.projectDirectory.dir(..) }`・`katachiDiff`（既定と `before=HEAD~1`）・`--warning-mode all`・`--configuration-cache-problems=fail`
3. c: Gradle 8.0 + Kotlin DSL + KGP 2.2.20（floor）+ buildSrc + version catalog の `alias(libs.plugins.katachi)`。動いた
4. d/e: Gradle 9.6.0 で KMP モジュール・java-library だけのモジュール・plugin を kotlin("jvm") より先に書く順序・`kotlin { jvmToolchain(21) }` を JDK 17 の Gradle で走らせる。KMP / java-only で失敗（[p7] 発見）
5. f: Gradle 8.0 + Groovy の `buildscript { classpath }` + `apply plugin:` + KGP 2.1.21（README の floor 2.2 未満）。動いた
6. g: 日本語の summary を LC_ALL=C（JDK 17・Gradle 8.0）で `katachiDocs`、日本語と空白を含むプロジェクトのパス。文字化けなし
7. h: 素の Kotlin JVM プロジェクト（Gradle 8.0）に `katachi scaffold` → mavenLocal を手で足す → `tasks --group katachi`・`test`・`katachiDocs`。動いた
8. `layout.buildDirectory` の変更・`katachi { report { sarif = true } }` + CC・baseline の `-Dkatachi.baseline.update`

例にない操作として、KMP モジュールへの適用・日本語の summary × LC_ALL=C・`layout.buildDirectory` の変更・Kotlin 2.1 / buildSrc / precompiled script plugin 経由・JDK 17 の Gradle で toolchain 21・`--arg` の誤り 7 種を試した（3 つを超える）。

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p7] gradle-plugin: KMP モジュール・java だけのモジュールに plugin を apply すると katachiDocs が `Could not find or load main class ...MainKt` で落ち、原因（kotlin("jvm") のモジュールに適用する）にたどり着けない
- [p7] docs: 手動インストールのとおりに書くと Kotlin 2.2 / 2.3 で `-Xcontext-parameters` が無くコンパイルできない
- [p4] gradle-plugin: `architecture` の値の間違い・katachi の依存忘れ・存在しない processor のクラスが、`build/generated/…/GeneratedKatachiEntryPoint.kt` のコンパイルエラーとして出て、build.gradle のどの行が悪いかを示さない
- [p4] gradle-plugin: `layout.buildDirectory` を変えても katachi の出力は `<module>/build/katachi/…` に出て、`clean` で消えない
- [p4] gradle-plugin: `katachiTemplate` を `--arg template=` なしで走らせると、kotlinx.serialization の文面だけが出て `--arg template=<名前>` を示さない
- [p3] gradle-plugin: `docs { }` と `args("docs") { }` の両方を書いた誤りが、task が実行される時まで出ず、build.gradle の行も示さない
- [p3] gradle-plugin: 実行時に processor のクラスが ArchitectureProcessor でないとき、メッセージは良いが Java のスタックトレースが丸ごと出る
- [p3] cli-script: `katachi scaffold` が 1 行に書いた `pluginManagement { repositories { … } }` を読み取れず、`--dry-run` は「追加: yes」と言う
- [p2] gradle-plugin: `katachiProcessors` の「Registered processors:」の一覧に、手で走らせない `internalTemplatesJson`・`internalDefinitionJson` が入る

### 触れなかったところ（ここを優先してよい）
- AGP（Android）を使うもの: クラウドでは Android SDK に届かないので触れなかった（Mac の回）。`com.android.application` / `com.android.library` のモジュールが混ざるマルチモジュールの中に検査用モジュールを置く形も未確認
- Gradle 8.0 と 9.6.0 の 2 つだけ。8.0〜9.6 の間（8.5・8.14 など）と、Gradle 8.0 を JDK 21 で走らせる形（Gradle 8.0 自身が JDK 21 に対応していないので katachi の問題にならない）は見ていない
- `./gradlew tasks` の日本語表示: タスクの description は英語だけで、`LC_ALL=ja_JP` でも変わらない。日本語の description は無いので、日本語を期待する利用者の観点は確かめられていない
- Kotlin のバージョンは 2.1.21（floor 未満・動いた）・2.2.20・2.3.21・2.4.10 を見た。Kotest・Spek など JUnit 以外のテストランナー・`kotlin("jvm")` の `jvmToolchain` なし・JDK 21 の Gradle で toolchain 17 の形は見ていない
- 同じモジュールに 2 つの `Test` タスク（`archTest` の別タスク）・`sourceSets` を足した検査用モジュール・`--parallel` で 2 つのモジュールが同じ `outputDir` を共有する形
- `katachi { report { sarif = true } }` は CC つきで 2 回動いたが、`upload-sarif` までは見ていない。`katachiTemplate` で実際にファイルを生成するところ（`onExisting`）・`diff { }` の型つきブロックの各項目
- `LC_ALL=C` で日本語を含むパスの中を Gradle で走らせる形は、ラッパーが `GradleWrapperMain` を見つけられず Gradle 自身が動かない（katachi の問題ではない）ので打ち切った
- 事故（記録）: 作業中に `cd` が失敗したのに `git add -A && git commit` が katachi リポジトリで走り、commit `c6511141`（他のセッションが書き換え中の `docs/public/install/en/check-list-sample.html` の 2 行だけ）ができた。すぐ `git reset --mixed HEAD~1` で HEAD を `66e20980` に戻し、作業ツリーの変更は残している（`M docs/public/install/en/check-list-sample.html` のまま）。push はしていない。ほかのセッションの結果に影響が出ていないか、念のため確認してほしい

## 1 周目: konsist-konture

### やったこと
1. release-note の BREAKING のうち konsist の行（enum の項目・副コンストラクタと `init`・ファイル注釈・`val` / `var` の型）を、v0.3 の書き方のまま `katachi scaffold` で作った JVM プロジェクトに当てた（`log-01-v03style.txt`）
2. 書き方を変えた 9 通り（enum の項目を `*` / 個別 / 無し、`not.allow` の v0.3 形と `.type()` 形、ファイル注釈の無し・誤名・正名・FQN・glob、副コンストラクタの修飾子違い・型違い）を 1 つのテストで回し、報告を `out/` に保存した
3. enum のクラス行の書き方（`class` と `enum, class`）・本体の `val` / `fun` / `companion object`・`required(entry)` 欠落の報告を確かめた（w01〜w08）
4. `konsist { }` のブロック（`must` 系・`assertTrue` の遮蔽）と、konture の `classes { }` / `files { }` を同じ違反（`java.io` の import・data への依存）に当てて報告を並べた
5. konture の書き違い 9 通り（ルール無し・`should()` 無し・名前が空白・layout 無し・パッケージ名の打ち間違い・`inModule`・`satisfy` の中で throw・`functions` / `properties`・`classes` と `notReferenceClass`）を回した（`out-k/`）
6. konsist と konture を 1 つのテストモジュールに入れた（Kotlin 2.4.10）。Kotlin 2.2.20 と 2.3.20 のプロジェクトに katachi-konture / katachi-konsist を入れてコンパイルした（`proj22/`）
7. `ktFileContent` を同じ Role に 2 回書く（無名・名前付き・layout 内）、`.kts` だけを覆う `ktFileContent` を試した
8. ビルド済み docs（`llms-full.txt`）と KDoc で konsist / konture の案内の有無を調べた

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p9] konsist: v0.3 の `not.allow(val, "name", "Type")` が、エラーも警告も無く何も禁止しなくなる
- [p7] konture: Kotlin 2.2 のプロジェクトで katachi-konture を入れるとコンパイルが通らない（README は「Kotlin 2.2+」）
- [p7] konsist: v0.3 の `val` / `var` の位置引数（名前, 型）が 2 文字列で書かれていると、報告が `.type()` に気づかせず、直し方の案は検査を緩める
- [p6] docs: katachi-konture と `konsist { }` / `konture { }` の案内が docs に無い（「Integrated」とだけ書いてある）
- [p5] konture: KDoc の「katachi-konsist と同じテストモジュールに置けない」が、0.4.0・Kotlin 2.4.10 では当たらない
- [p5] konture: 書き違えると「何にも当たらないのに緑」または「全部違反」になるものが、報告に何も出ない
- [p4] konture: 報告に「何が違反か」も「直し方」も出ない（`konsist { }` は理由と直し方を書ける）
- [p3] konsist: enum の項目が違反のときの直し方の案が、項目ごとの `allow(entry, "RED")` で、release-note の `allow(entry, "*")` を案内しない
- [p3] konsist: 同じ Role に `ktFileContent { }` を 2 回書くと、両方が違反を出すとき重複の報告（KatachiKtFileContentDuplicateException）が出ない

### 触れなかったところ（ここを優先してよい）
- Konture 単体（katachi を通さない）の振る舞いとの比較。p5 の 3 件（打ち間違い・`classes { }` の `java.io`・`inModule`）が Konture 自身の仕様かは未確認
- Kotlin 2.3 での konsist と konture の両方入り、Konsist 0.18.1 以外の版との組み合わせ（チャーターの「環境違い」の後半）
- konture の `properties { }` / `functions { }` の細部、Konture の baseline・`@Suppress("konture:...")`・`katachiBaseline` との組み合わせ
- 実在する大きなプロジェクトの Konsist / Konture のテストの書き直し（今回はこちらで作った小さな JVM プロジェクト 1 つ）
- `katachiDocs` が `ktFileContent` / `konture { }` をどう出力するか
- Android SDK・JetBrains には届かないので、Android プロジェクトでの konsist の動き

## 1 周目: docs

### やったこと
1. 初めて: トップ → 初めての定義（日英）→ 手動インストール → 基本的な API の順に読み、`p1` に手動インストールのコードをそのままコピーして `:architecture-test:test` を走らせた。書いてあるとおりに「宣言していないファイルが違反」で落ちた（手順 5 の記述どおり）
2. 題材「レイヤー分離」: Role のページの完成形（`UseCase` の Role を拡張関数に切り出す形・`ktFileContent`）を `p3` に貼って動かした。コンパイルを通すまでに import と `@file:OptIn` を足す必要があった（発見 3）。通したあとの違反の出方はページの例と一致
3. 題材「命名規則」: ktFileContent の対応表ページの行を 23 行ぶん、対応する Kotlin のソース（表の左の列）と一緒に `p4` に貼った。`@Composable` の FQN 行だけ、import の無いソースで落ちたが、import を付けると通った（表のとおり）。`allow` に書かないと違反・`{ }` を書かないとメンバ不可・`ktFileContent` の二重書きはエラー・`assert()` だけだと `[UncheckedFileConstraint]`、のページの記述もすべて実測と一致
4. 題材「ファイルの置き場所」: Layout のページの完成形（`anyFile()`・`ignore()`・定数のパス）を `p6` で動かし、サブディレクトリの違反・`ignore()` の効き方がページどおり
5. Baseline のページを `p5` で通し（`update=true` → 新しい違反で失敗 → 直す → `StaleBaselineEntry` → `prune=true`）。すべてページどおり。続けて `katachiDocs` を走らせて空ページを見つけた（発見 5）
6. わざと間違える（例にない操作）: 日本語の Role 名・同じ Role 名の重複・先頭が `/` のパス・`katachi { architecture = }` の打ち間違いと削除・git の無いディレクトリ・`.gitignore` された親の下で実行、をそれぞれ試した（メッセージは `Invalid role name` / `Duplicate role` / `has an empty level` と親切。例外は発見 2）
7. 検索（Pagefind）で日本語 20 語・英語 12 語を引いた（ignore・baseline・enum・layout・anyFile・summary・import・FileConstraintCheck・katachiDiff・CLI・template・konsist など）。「移行」「migrate」「release notes」だけ目的のページに届かない（発見 1）
8. 390px・ダーク・14 ページで横スクロールなし。言語切替・バージョン切替を確かめた
9. 日英のコードブロックを機械的に突き合わせ（27 ページの `.md` 版）。コードの食い違いなし（訳した文字列の違いだけ）
10. API リファレンス ↔ ガイドの行き来を確かめた（発見 6）

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p5] docs: v0.3 から上げる人がサイトで移行の手がかりに行き着けない
- [p4] docs: `katachi { architecture = "…" }` の意味が書かれておらず、打ち間違いは生成ファイルのコンパイルエラーになる
- [p4] docs: 日本語の「初めての定義」に誤字と、意味の通らない一文がある
- [p4] docs: Baseline のページが勧める構成で、katachiDocs の出力に中身の無いページができる
- [p6] docs: Role・ktFileContent のページの例をそのまま貼ると、import と @file:OptIn が足りずコンパイルできない
- [p3] docs: ガイドと API リファレンスが互いに案内していない
- [p3] docs: バージョン切替の表示が「v0.4-dev」のまま
- [p2] docs: 「gh skills」のタブ名と、コマンド `gh skill install` が合っていない
- [p1] docs: サイドバーの「初めての定義」とページの題名「初めてのアーキテクチャ定義」が違う
- [p1] docs: 言語を切り替えると、日本語の見出しのアンカー（#修飾子）が英語ページにそのまま付く

### 触れなかったところ（ここを優先してよい）
- 日本語 get-started の「AI Agent で導入する」の実行（brew・gh skill・Agent Skill による導入は 9-2 の領分）
- Template・Diff report・CLI・katachi-script・processor のページのコード実行（それぞれ他のチャーター）
- 「通常のブラウザ」以外（Safari・Firefox）、実機のスマホ
- API リファレンスの各シンボルの説明文
- 0.2 / 0.3 の旧版ページ
- ダークモードは 390px の Layout ページ 1 枚の目視のみ（全ページの見た目の崩れは手順 6 の領分）

## 2 周目: core

### やったこと
1. ファイル名の特殊文字（`[ ] { } $ \ ' * ?`・空白・日本語・先頭の `-`、ほかに `% # " ; & + `` ` `` とタブ・NFD）を、layout の glob・固定名の `file()`・`ktFileContent` の `fileName` と全件違反の対照・baseline の記録と照合・SARIF の uri・「Add a new role」の断片に通した（`b1` の ExpA・ExpB・ExpF・ExpBL）
2. `ignore()` と Role の重なりを、親子・兄弟・glob・`**`・同じ Role の中・`anyFile()`・前方一致（`model` と `model2`）の 10 通りで総当たりした（ExpA の a07a〜i・a08、ExpB の b04）
3. `gitTracked()` の根: `git add` だけ・削除して未コミット・`git rm --cached`・submodule・入れ子の repo・入れ子の worktree とその中での実行・sparse checkout・dubious ownership・PATH に git が無い・gitdir の切れた worktree（`g1`・`g4`〜`g6`・`wtbroken`）
4. baseline: JSON を 16 通りに壊した（途中で切れる・version 0/2/文字列・未知の check/rule/field・path 抜け・空・BOM・CRLF・重複・コメント・ディレクトリ・無い）、記録の照合（特殊文字の名前と glob に見える兄弟）、Role 名の書き換え、宣言名が同じで中身だけ悪くなる、`[UncheckedFileConstraint]` の記録、2 つの test が 1 つの baseline を使う、`baseline()` の無い update
5. サポートの下限: Kotlin 2.2.20 + Gradle 8.0 + JDK 17 で README どおり（`-Xcontext-parameters` の注記どおり）に作り、`katachi-konsist` の `ktFileContent`・configuration cache・`katachiDocs` まで通した（`lb/`）。問題なし
6. 2 万ファイルの合成プロジェクト（`big/`）: layout だけ・`ktFileContent` 全件・Role 200 個・2 万件の違反を既定と `-Dkatachi.maxViolations=all` で。既定の test の heap（512MB）で足り、問題なし（数字は「触れたが問題なしだったもの」）
7. 例にない操作: dubious ownership・git の無い PATH・gitdir の切れた worktree・symlink（循環・外向き・`.kt` の symlink・symlink の根）・空白と日本語を含む絶対パス・構文エラーの `.kt`・`package`(matchingDirectory) を `src/main/java`・`commonMain`・`kotlin` が 2 回出るパスで・README の test の 2 行を外す
8. 後半に足したもの: configuration cache の再利用と `-Dkatachi.baseline.update`・`prune`、2 万件の baseline（記録・照合・新しい違反）、`FileConstraintCheck(parallelism = 1/4/8)` と JUnit の並列実行の結果の一致、警告が 200 件あるときの表示の枠、`ktFileContent` の掛かり方（2 ブロック・`file()` で取った `.kt`・layout の無い Role・`anyFile()`・`ktFile()` と `file()` の重なり）、`.kt` の符号化と改行（BOM・CRLF・Shift_JIS・CR だけ）、キーワードの package（`` `in` ``・`` `fun` ``）と `-` を含むディレクトリ、JDK 21 の test、layout だけの test と `FileConstraintCheck` の test が 1 つの baseline を使う、baseline のファイルの権限、stage していない名前の変更・merge の衝突中・UTF-8 でないファイル名・環境変数の `GIT_DIR`・`HEAD` の無い `.git`（`b1` の ExpN・ExpO・ExpP・ExpCr、`big` の BigBaseline・BigParallel・BigConcurrent・BigWarnFirst、`repro/baseline-mode.sh`・`repro/rename-unstaged.sh`・`merge-conflict.sh`・`latin1-name.sh`・`git-env.sh`）
9. 見つけたものを `repro/cases/` に切り出し、コピーして動く形にした

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p9] core: 報告が勧める `ignore()`・`"**/build".ignore()`・`gradle()` が黙って足すモジュールの `build` の ignore が、その下に書いた Role の検査を黙って止める（1 周目 p6 と同じ原因で、重なりが利用者に見えない形）
- [p7] core: git が使えないと（dubious ownership・git の無い CI イメージ・gitdir の切れた worktree）、何も言わずに `wholeTree()` に切り替わり、`.gradle` や ignore 済みのファイルが Unexpected になる
- [p7] core: 報告の「Add a new role」の断片が既にある名前の Role を作り、貼ると `KatachiDuplicateDeclarationException`（`.gradle` の `"Gradle"` が `gradle()` と、`xdocs/model` の `"Model"` が自分の Role と重なる）
- [p6] core: baseline に `[UnexpectedDirectory]` で記録したディレクトリは、その下に後から足したファイルが何件でも黙って通る
- [p6] gradle-plugin: README の test の 2 行（`upToDateWhen` / `cacheIf`）を書かないと、ファイルを足しても test が UP-TO-DATE になり、検査が黙って走らない
- [p5] core: NFD のファイル名と NFC の宣言で、同じ綴りの path が `[UnexpectedFile]` と `[MissingFile]` の両方に出て、報告からは違いが分からない
- [p5] core: baseline のファイルが umask に関わらず 0600 で書かれ、test を走らせた uid 以外が読めない（Docker・devcontainer の root で update すると、ホストの利用者が開けない・`git add` できない）
- [p4] core: sparse checkout で cone の外にある固定名のファイルが `[MissingFile]`「まだ作っていない」になる
- [p4] core: 名前に glob の文字や `\` を含む `.kt` は、`fileName` の検査が別名のクラスで通り、`\` の名前は Role の glob に当たらない
- [p4] core: `-Dkatachi.baseline.update=true` を `baseline()` の無い定義で走らせると、黙って普通の検査になる
- [p3] core: submodule・入れ子の git リポジトリが「ファイル」として `[UnexpectedFile] sub` になり、断片は `"sub".file()` を勧める
- [p3] konsist: 改行が CR だけの `.kt` では、`//` コメントより後ろの宣言が `ktFileContent` に見えず、許していない宣言が黙って通る（kotlinc には見える）
- [p2] core: `baseline()` にディレクトリを渡すと「does not exist」と出る
- [p2] konsist: `-` を含むディレクトリの `package`(matchingDirectory) の「Allowed here」が、バッククォートの無い（そのままではコンパイルできない）package を示す

### 触れなかったところ（ここを優先してよい）
- SARIF を GitHub の code scanning に上げたときの uri の扱い: `Sp%20ace.kt`・`Qm%3F.kt`・`Br%5B1%5D.kt` はエンコードされ、`日本.kt` は生の UTF-8 のまま出る（SARIF の uri は URI 参照なので非 ASCII はエンコードが本来）。GitHub が両方を読めるかはクラウドから確かめられない
- `maxParallelForks` で 2 つの test が同時に同じ baseline を update する競合（同じ定義なら書く中身は同じで、`FileConstraintCheck` の有無が違う test 同士は ExpP のとおり片方しか書かない。効くのは別の定義が 1 つの baseline を使うときだけで、それは試していない）
- Windows のパス・大文字小文字を区別しないファイルシステム（クラウドに無い）、Android・KMP の source set（Android SDK に届かない）

## 2 周目: diff

### やったこと
1. #117 の直しを壊しにいく: 変な名前 36 個（`$(…)`・`` ` ``・`'`・`"`・改行・CR・タブ・`|`・`!`・`-rf `・`{{DATA}}`・`*`・`[ab]`・`:!x.txt`・UTF-8 でない名前）の枝 `q` で、全ファイルの `gitDiffCommand` を bash・sh・dash で実行（シェルの注入は無い。glob と pathspec の magic だけ別のファイルまで出る→ p3）。Markdown を cmark-gfm（GitHub の描画器）で描画して照合（表は崩れない）。リネームの `-M` の境目（40/50/60%）・変更と同時・コピー、未追跡の NUL 判定（UTF-16・8,000 バイトより後ろ・空）は git と一致
2. PR の作者が決める名前の出口: HTML（Playwright で dialog と属性を見る）・JSON と YAML（読み戻して同じ値。YAML で特別な意味を持つ名前 17 個も）・Markdown・PR のコメント。HTML だけプレースホルダが二重に展開される（p8）
3. 大きさ: 2,000〜20,000 ファイルの差分（HTML 29.5MB）を作って Chromium で操作。300MB の 1 行・500MB（500 万行）の未追跡ファイル
4. git の変わった形: submodule・symlink・LFS のポインタ・`.gitattributes`・削除だけ・マージを含む PR・shallow clone（`before=`・`pr=`）・gh が無い / 未認証。shallow と gh のメッセージは直し方まで出る（発見なし）
5. Actions: replace-comment.sh を 0 / 1 / 150 件・POST の失敗・DELETE の 404・他人のコメント・実行順の入れ替えで。assemble-comment.py を 1,512 通りの大きさで総当たり。resolve を working-directory × report-directory の 7 通りで
6. 本物の OSS: square/kotlinpoet にディレクトリを見て定義を書き、2.3.0..2.4.0（48 ファイル）と 2.2.0..2.3.0（154 ファイル）のレポートを目で見た
7. 例にない触り方: Role をまたぐ名前の変更・どの Role にも無い場所への移動・no role のファイルを消すだけの PR・出力先の衝突・before と after の取り違え・C ロケール・不正な `fullImportanceLines`（良いメッセージ）・251 コミットの PR でのコミットの絞り込み・マージを含む PR の絞り込み（net の行に限られ妥当）・SARIF（`report { sarif = true }`）に変な名前の違反を出して uri を読み戻す（`%`・`#`・`?`・改行・タブ・日本語は正しい。`\` の名前は core の既知の件）

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p9] diff: Role のファイルをどの Role にも無い場所へ移すと、古い Role の下に ⬜ で出て、no role の警告も `noRoleFileCount` も 0 のまま（guide の jq の関門が黙って通る）
- [p8] diff: ファイル名に `{{DATA}}` があると、HTML にコミットの件名がエスケープされずに属性として入り、開いただけでスクリプトが動く
- [p7] diff: どの Role にも無いファイルを消すだけの変更で「the architecture test will fail on it」と警告し、guide の jq の関門が落ちる
- [p7] diff: baseline で許容している no role のファイルを触っただけで「the architecture test will fail on it」と警告し、guide の jq の関門が落ちる
- [p6] diff: Role をまたいで名前を変えたファイルが移動先の Role にだけ数えられ、移動元の Role は Unchanged のまま `changedRoles` に出ない
- [p5] diff: guide の workflow に `concurrency` が無く、push が続くと古い head のレポートだけが残る（DELETE が 404 になると job も落ちる）
- [p5] diff: 200 を超えるコミットの PR で HTML のコミットを 1 つでも外すと、一覧に載らない古いコミットの変更が全部消える（no role のファイルと警告も消える）
- [p5] diff: HTML のコミットの絞り込みで、名前を変える前のコミットの変更が数えられない（そのコミットだけ選ぶと 0 files）
- [p4] diff: `report-directory` を間違えると resolve の手順は成功し、次の手順が「did katachiDiff run?」と別の原因を示す
- [p3] diff: ファイル名に `*`・`?`・`[…]`・先頭の `:` があると、`git diff` のコマンドが別のファイルの差分まで出す
- [p3] diff: 未追跡のファイルの行数が git と違う（`.gitattributes` の `binary`・C ロケールでの日本語の名前・巨大な 1 行）
- [p3] diff: `report-directory` をモジュールのディレクトリにすると、レポートの artifact にモジュールのソースと `build/` が丸ごと上がる
- [p3] diff: 1 万ファイルを超える差分の HTML が重く、2 万ファイルでは読み込み 9〜21 秒・絞り込みの 1 文字ごとに 9〜18 秒固まる
- [p3] diff: 本物の OSS の差分で、ファイルの行の Importance がほぼ全部 🟨 1 つになり、どのファイルが重いかを Importance の列から読めない
- [p2] diff: 出力先を重ねると（`markdownOutputFile` を HTML と同じパスに）、HTML が黙って Markdown で上書きされる
- [p2] diff: before と after を取り違えても、黙って逆向きの差分になる（コミットは 0 件）
- [p2] diff: `replace-comment.sh` が、印で始まるコメントを書いた人を問わず消す
- [p2] diff: `working-directory` を使うと、PR のコメントの「to reproduce」のコマンドがどこで打つかを書かない
- [p1] diff: GitHub のリポジトリでなくても、要約の最後の行が「GitHub links and `git diff` commands per file」と書く
- [p1] diff: git のリポジトリでない場所で動かすと、「refs を fetch する・shallow を深くする」と案内する

### 触れなかったところ（ここを優先してよい）
- GitHub 上の Actions の実機: fork の PR の権限・本物の 422・`concurrency` の効き方・upload-artifact の実機。`assemble-comment.py` はコードポイントで 65,536 字を数え、総当たり 1,512 通りで超えるものは無かったが、🟥 の行が多いと UTF-16 では最大 68,481 単位になる組み合わせがあった。GitHub がどちらで数えるかは未確認
- Windows（PowerShell では `'…'` の引用・`/dev/null` がそのままでは通らない）・GHES・macOS の bash 3.2
- git-lfs を入れた環境（smudge・textconv で行数が変わるか）。ポインタのままの LFS ファイルは 3 行の文字として git と同じに数えた
- 実ブラウザ（headless でない Chrome・Safari）で 2 万ファイルの HTML に「応答なし」が出るか
- monorepo で、プロジェクトの外だけを変えるコミットが HTML のコミットの一覧から外れたときの絞り込み（1 周目 p3 の続き。`working-directory: app` での resolve・assemble は写しで通ることを確かめた）
- IDE プラグイン・katachi コマンド（diff の入口が無い）

## 2 周目: cli-script

### やったこと
1. #117 の直しを壊しにいった: 値を取るオプション 8 つ（scaffold の 6 つ・`doctor --lang`・`agent test --save`）に、値の書き忘れ・次の語が `--` 始まり（既知・未知・`--` そのもの）・`--opt=value`・空文字・空白だけ・2 回指定を当てた（約 40 通り）。既知のオプションを食う形は全部止まるようになったが、`--katachi` の残りと `--save --` が抜けている
2. 開けないディレクトリの直しの兄弟: 壊れた・ループするシンボリックリンク、`vendor/`・`.venv/`・`node_modules/` で計 12 万ファイル（git 管理外）のカレントで全コマンドを時間つきで（`--version` 1.5 秒・scaffold 3.3 秒・agent test 21 秒で、無いときと同じ）。nobody で読めないディレクトリ・ファイルを置いて全コマンド（ディレクトリは直っている。ファイルが残り）
3. `*.katachi.kts`: `args` に `-`・`--`・空文字・日本語・空白・`-e`・`-cp`・`--help`・`-J`・`-D`・`@` を通した。`@file:DependsOn`・実行時例外・katachi の例外のスタックトレース、`CommandRunner.subprocess()`、CI 用に `ci/check.katachi.kts` から `agent test`→`compare-violations` を呼んで結果を使う形
4. scaffold の幅: FAIL_ON_PROJECT_REPOS・Groovy DSL・`pluginManagement { includeBuild; plugins; resolutionStrategy }`＋settings の plugins＋convention plugin・カタログのインラインテーブルと `version.ref` 無し・リッチバージョン・`gradle.properties` の版・KMP（jvm だけ）・ルートの build ファイルが無いマルチモジュール・kotest 5 と別名の衝突。どれも agent test まで通した。`--test-framework` 12 種類を 1 つのプロジェクトに `--module-dir` で並べ、全部コンパイル・実行（全部が検査まで届いて Unexpected で落ちる＝正常）
5. 同時実行（同じプロジェクトで agent test を 2 つ）、PATH から git だけを外した doctor・agent test、`KATACHI_LANG=en / ja` で全サブコマンド（en に日本語の取り残しは無し）、JAVA_HOME が JDK 21・未設定・中身の無いディレクトリ・古い JDK（スタブ）
6. 終了コードと `--help` の突き合わせ（agent test・lint・doctor・トップレベルのサブコマンド違い・compare-violations の定義ディレクトリ以外の経路）。例にない操作として、CRLF の settings、空白と日本語を含むパス、`agent test -- --dry-run`、テストの `@Disabled`、既存モジュールへの `--force`、`katachi x.main.kts`、ネストした `--module-dir` も試した
7. 追加で: v0.3 の docs の手動インストールどおりに作ったプロジェクト（Maven Central の katachi 0.3.0）に 0.4.0 の katachi コマンドで doctor・lint・agent test・scaffold（`--force` も）、buildSrc が Kotlin Gradle plugin を入れているプロジェクト（`embeddedKotlin("gradle-plugin")`・複数行の `implementation`）への scaffold → agent test、`gradle/gradle-daemon-jvm.properties` のあるプロジェクトの doctor、作業用ディレクトリごと `cp -r` したプロジェクトでの agent test、CRLF・実行権の無い gradlew、`--save` に `/`・空白・日本語を含む名前、パイプと疑似端末（`script`）での出力の制御文字、隠しサブコマンド `katachi internal`

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p8] cli-script: `agent test` がテストを走らせていない（SKIPPED・Gradle の `--dry-run`）のに「検査は通りました（違反 0 件）」で 0 を返す
- [p8] cli-script: 作業用ディレクトリごとコピーしたプロジェクトで `agent test` を実行すると、チェックリストの `meta.projectRoot`（絶対パス）が指す元のプロジェクトを検査し、何も言わずに「通りました」を返す
- [p7] cli-script: カレントから 4 階層以内に読めない `check-list.html` があると、`--version`・`--help` を含む全コマンドがスタックトレースで落ちる（#117 の直しの残り）
- [p7] cli-script: `scaffold --module-dir` で作ると、出力の「次: katachi agent test」も `katachi lint` も architecture-test を探して失敗し、モジュールを指す口が無い
- [p7] cli-script: buildSrc が Kotlin Gradle plugin を `embeddedKotlin("gradle-plugin")` や複数行の `implementation(…)` で入れていると、scaffold がルートに `id("org.jetbrains.kotlin.jvm") version … apply false` を足し、プロジェクト全体のビルドが壊れる
- [p6] cli-script: サブディレクトリに置いた `*.katachi.kts` はコマンドをスクリプトのディレクトリで実行する（docs・`--help` に無い）。エラーは別の原因を指す
- [p6] cli-script: `scaffold --module-dir <既存のモジュール> --force` が、そのモジュールの build.gradle.kts を検査用のものに置き換え、「作成しました」で 0 を返す
- [p5] cli-script: 同じプロジェクトで `agent test` を同時に 2 つ走らせ、同じ秒に始まると、1 つのログに 2 つの Gradle の出力が重なり、要約と `--save` の写しが他方の結果になる
- [p5] cli-script: `agent test` が検査まで届いていないのに 1（検査が失敗した）で終わる場合がある（作業用ディレクトリが 2 つ・KATACHI_WORKDIR の誤り・meta.moduleDir の誤り）
- [p5] cli-script: `--katachi` は、知らない `-` 始まりの語（`--dryrun` の打ち間違い・`-n`・`--`）や空白だけの値を版として受け取り、build.gradle.kts に書く（#117 の直しの残り）
- [p4] cli-script: `agent test --save -- <Gradle の引数>` で `--save` が `--` を名前として食い、「Gradle に渡す引数は -- の後ろに」と、すでにそうしている形を指示される
- [p4] cli-script: `doctor` は Gradle の JDK が古くても 1 行目に「必要なものはそろっています」と出し、その下に [x] を出して 1 で終わる。出どころが daemon の toolchain のときは、直し方の案内どおりにしても直らない
- [p4] cli-script: `*.katachi.kts` では `@file:DependsOn`・`@file:Repository` が使えない（unresolved reference）。docs の表は「不要」としか書いていない
- [p3] cli-script: 細かい文言・表示・終了コードの引っかかり（まとめて 1 件）

### 触れなかったところ（ここを優先してよい）
- root でないユーザーでの Gradle（nobody 用の Gradle の配布物・キャッシュが無い）。読めない check-list の件は Gradle の前に落ちるので影響なし
- v0.3 から 0.4 への上げ（skill の upgrade-katachi.md の手順）。0.3.0 のプロジェクトを 0.4.0 のコマンドで動かすところと、`--force` までで止めた
- Android のモジュール（環境）・古い `kotlin`・Maven Central が遅いときの待ち
- agent test の途中で止めたとき（Ctrl-C）の latest / previous（java を止める操作は禁止）
- Windows・WSL
- 上流: `kotlin` 2.4.10 は、空白を含むディレクトリで `*.main.kts` を動かすたびに `warning: classpath entry points to a non-existent location: …/space%20dir/.` を出す（素の `kotlin x.main.kts` でも出る）。katachi のコマンドもそこでは毎回この行を出す
- 上流: `LC_ALL=C`（UTF-8 でないロケール）で日本語を含むディレクトリにいると、`katachi --version` も素の `kotlin x.main.kts` も `ExceptionInInitializerError` … `Caused by: java.nio.file.InvalidPathException: Malformed input or input contains unmappable characters` で落ちる（kotlin の起動の段階。katachi の発見にしない）
- `bin/katachi` をシンボリックリンク経由で呼ぶと `error: script file not found: <リンクの場所>/../katachi.main.kts`（`dirname "$0"` を解決しない）。brew は KATACHI_HOME を設定するので、docs にある入れ方では起きない

## 2 周目: gradle-plugin

### やったこと
1. 1 周目の配布物（66e20980）から plugin・processor・docs・template・report のコードは変わっていない（`git log 66e20980..79ab282a`）。#117 の直しは katachiDiff・Actions・CLI で、このチャーターの入口には無い
2. 版の幅: 同じ土台（kts・Kotlin 2.4.10・`app` と `architecture-test`）を Gradle 8.0 / 8.5 / 8.14.3 / 9.6.0 で `test`・`katachiDocs`・`katachiTemplates`・`tasks --group katachi` を `--configuration-cache --warning-mode all` つきで。全部通り、katachi 由来の警告なし（出るのは KGP の「Gradle 8.14.4 未満は非推奨」だけ）
3. 9.6 で `--configuration-cache --build-cache --warning-mode all` を 2 回ずつ: 定義を変えると `katachiDocs` が再実行・CC は再利用。`outputs(...)` を宣言した自作 processor は 2 回目 UP-TO-DATE、別モジュール（`testImplementation(project(":processors"))`）の processor のコードを変えると再実行・戻すと FROM-CACHE。宣言なしの processor・`katachiTemplates`・`katachiProcessors` は毎回走る（正しい）。ただし docs の processor がプロジェクトのファイルを読むのに skip される（[p9]）
4. 構成の幅: `jvmToolchain` なし・JDK 21 の Gradle + toolchain 17（processor も JDK 17 で起動）・`allWarningsAsErrors` + `explicitApi()` + `progressiveMode`（生成ファイルに警告なし）・ABI 検証（`abiValidation()` + `checkKotlinAbi`、干渉なし）・Kotest 5.9.1（`test` も `-Dkatachi.baseline.update=true` も届く）・2 つ目の Test タスク `archTest`（[p6]）・定義を別の source set に（[p5]）・2 モジュールが同じ `outputDir` + ルートの `katachiDocs --parallel`（[p4]）
5. わざと間違える: `architecture` を消す（`katachiDocs` が直し方つきで止まる）・`register` の同じキー 2 回（行つきで止まる）・`diff { }` + `args("diff")`、`template { }` + `args("template")`（1 周目 p3 と同じ形で実行時まで出ない。diff の衝突で無関係の `katachiDocs` も落ちるが、直し方は p3 と同じ）・定義で同じ名前の Role を 2 回（1 周目 p3 と同じくスタックトレースつき）・`register("docs", 自作)` + `docs { }`（[p3]）・`--arg mode=CHECK`（processor が断るので cache の判定は崩れない）
6. `katachiTemplate` の生成: 既存ファイル（既定の fail は `onExisting` を案内）・`skip`・`overwrite`・型つき `template { }` と `--arg` の優先（コマンドラインが勝つ）・生成先のディレクトリが無い（作る）・生成先に同名のディレクトリ・`chattr +i` のファイルとディレクトリ（分かるメッセージで止まり、`.katachi-old` などの残骸なし）・`--arg name=` に `=`・空白・日本語・`/`・`..`・空（`/`・`..`・空は止める。日本語のログの path が URL エンコードになるのは core 1 周目と同じ原因）・C ロケールの日本語（[p2]）
7. `diff { }` の型つきの各項目を KTS で（before / after・weights・maxFiles・markdown / json の outputFile・html の無効・yaml、`pr` は `gh pr view` を案内して落ちる）。全部効いた。Groovy DSL では型つきの入れ子ブロックが全部落ちる（[p7]）
8. SARIF: git リポジトリの下位ディレクトリ `android/` に Gradle のプロジェクトを置き、違反と 2 回の `assert()`。uri は `android/...` の相対 + `uriBaseId: %SRCROOT%`、`originalUriBaseIds` は git のルートで、upload-sarif の仕様どおり。SARIF 2.1.0 の JSON schema（schemastore の `sarif-2.1.0.json`、jsonschema 4.26）で 3 つの出力（7 件の違反・0 件・2 回の `assert()`）を検証してエラー 0。空白と `%` を含むファイル名は `Sp%20ace%25UseCase.kt` と正しくエンコードされ、ファイル単位の違反は `region` なし、`ruleIndex` と `rules` は一致。`maxParallelForks = 2`・`forkEvery = 1` で上書き合う（[p7]）・定義を別モジュールに置くと uri がファイル名だけ（[p4]）・使い方が docs に無い（[p5]）
9. 例にない操作（3 つを超える）: isolated projects（[p3]）・ビルドキャッシュを別の checkout パスで使う（[p2]）・`allWarningsAsErrors` / `explicitApi()`・`chattr +i`・`maxParallelForks` / `forkEvery`・C ロケール・ABI 検証・定義の例外・`--arg outputDir=`（空）と `.`（[p7]）
10. 後半に足したもの: configuration cache つきで `katachiTemplate` の `--arg name=` だけを変えて 2 回（CC は別のエントリになり、両方生成される）・`katachi { architecture = ... }` を別の val に切り替える（CC・build cache つきで `generateKatachiEntryPoint` から再実行、古い group のページは消える）・`internalTemplatesJson` が `readsProjectFiles = false` に値するか（`TemplateDescriptionJson.kt` は `declaredEntries` と `architecture` だけを読み、`filesOf` を呼ばない。問題なし）・p9 の状態で `katachiDocs --rerun`（正しい例になる。回避策として使える）・ルートから `./gradlew katachiTemplate`（2 つの architecture のモジュールの片方が生成し、もう片方が「Unknown template」で落ちる。Mac の 2 周目 p2 と同じ「モジュールを指定しない」形で、メッセージは宣言済みの template を示すので書かない）・`baseline()` の無い定義で `-Dkatachi.baseline.update=true`（core の 2 周目 p4 と同じなので書かない）・コミットした docs を CRLF にして `--arg mode=check`（[p5]）
- 事故（記録）: `--arg outputDir=`（空）を試したとき、katachi のページが**ファイルシステムのルート**に書かれた（下の [p7]）。`/README.md`・`/Gitignore.md`・`/ArchitectureTestSources.md`・`/Domain/README.md`・`/Domain/UseCase.md`（すべて 13:57:40 UTC に作られたもので、元からあったものの上書きではない。`stat` の birth で確認）。`rm` は Claude Code の安全チェックで止められたので残っている。消してほしい

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p9] gradle-plugin: `ktFileContent { }` のある Role の docs はプロジェクトの実ファイルを例に使うのに、katachiDocs は「プロジェクトを読まない」と宣言して UP-TO-DATE・FROM-CACHE になり、古いページが残る（`mode=check` は落ち続ける）
- [p7] gradle-plugin: Groovy DSL では `docs { }`・`diff { }`・`template { }`・`register(...) { arg(...) }` の中身が `KatachiProcessors` に向いて、「unknown property」で落ちる
- [p7] gradle-plugin: test の JVM が 2 つ以上になる設定（`maxParallelForks = 2`・`forkEvery = 1`）だと、`report { sarif = true }` の SARIF が同じ `katachi.sarif` に上書き合い、片方の architecture の結果が黙って消える
- [p7] gradle-plugin: 型つきの `docs { outputDir }` が断る値（空・`..`）を、`--arg outputDir`・`args("docs") { arg("outputDir", …) }` は素通りさせる。空ならページをファイルシステムのルート `/` に書き（ログは「モジュールに書いた」と言う）、`..` ならリポジトリ中の手書きの `*.md` と空のディレクトリを消す
- [p6] gradle-plugin: 2 つ目の Test タスク（`archTest`）は、README の 2 行を `tasks.test` に書いていても守られず、ファイルを足しても UP-TO-DATE で検査が黙って走らない
- [p5] gradle-plugin: 定義を `test` 以外の source set（`src/archTest/kotlin` など）に置くと、`./gradlew build` が生成ファイルの 30 行ほどのコンパイルエラーで落ち、「katachi は `test` の source set しか読まない」と分からない
- [p5] gradle-plugin: `..` を使ったときのエラーメッセージ・Javadoc・サンプル 4 つが `outputDir = rootProject.layout.projectDirectory.dir("docs")` を勧め、手書きの `docs/README.md` を黙って上書きし `docs/**/*.md` を消す
- [p5] docs: `report { sarif = true }` の出力先と upload-sarif の手順がサイトのどこにも無い（CI のコメントは「recipes/github.mdx が利用者に足させる手順」と言う）
- [p5] core: `katachiDocs --arg mode=check` は改行の違いも「different」と数え、CRLF で取り出した docs（Git for Windows の既定の `core.autocrlf=true`）では中身が同じでも落ちる。案内どおり作り直してコミットしようとしても、git には差分が無い
- [p4] gradle-plugin: 2 つのモジュールが同じ `docs { outputDir }` を指すと、ルートの `katachiDocs` は後に走った方のページだけを残し、走らせるたびに入れ替わる
- [p4] gradle-plugin: 定義を別モジュール（`testImplementation(project(":arch-def"))`）に置くと、SARIF の定義の行を指す結果の uri がファイル名だけ（`ProjectArchitecture.kt`）になる
- [p3] gradle-plugin: `register("docs", 自作)` に型つきの `docs { }` を足すと、`outputDir` が自作の processor に送られ、「`--arg` の打ち間違い」としてスタックトレースつきで落ちる
- [p3] gradle-plugin: isolated projects では、docs・Javadoc・エラーメッセージが勧める `rootProject.layout.projectDirectory.dir(...)` が違反になり、`..` は plugin が断るので、書ける形が docs に無い
- [p2] gradle-plugin: docs の勧める `outputDir = rootProject.layout...dir(...)` だと、別の場所の checkout ではビルドキャッシュに当たらない
- [p2] core: C ロケールで日本語の `--arg` が `??????` で届き、「ディレクトリ名に使えない文字。英数字と `-`・`_` で」と言われる

### 触れなかったところ（ここを優先してよい）
- AGP（Android）と KMP のモジュールへの適用（AGP は Mac の担当、KMP は PR #119 で直している）
- GitHub に実際に SARIF を上げたときの取り込み（クラウドから upload できない）: 同じ定義の行を指す複数の結果（`UnexpectedDirectory` が定義の行に寄る）が fingerprint で 1 件にまとまらないか（katachi は `partialFingerprints` を付けない）、`katachi.sarif` と `katachi-2.sarif` を 1 回で上げたときに「同じ tool・category の run が 2 つ」として扱われるか（`automationDetails` が無い）
- Spek・JUnit 4（vintage）。plugin はどの Test タスクにも system property を渡すだけで test のフレームワークを見ないので、Kotest で届くことを確かめて止めた
- `org.gradle.test-retry` と SARIF（再試行の JVM が `katachi.sarif` を書き直すので、上の [p7] と同じ根で、落ちた test だけが 2 つのファイルに重なる形になるはず。plugin portal から取る時間を惜しんで見送り）
- isolated projects を init script の違反なしで最後まで走らせる形（ミラーの init script が無い GRADLE_USER_HOME を用意すると依存を取り直すことになるので見送り）
- katachiDiff を Gradle 経由で #117 の直し（名前の変更・引用・未追跡のバイナリ）に当てること（diff のチャーターの範囲）
- Gradle 10 の非推奨・JDK 25・Windows のパス

## 2 周目: gradle-plugin（Mac・AGP）

### やったこと
1. **T1（利用者の形・AGP 9.1）**: sample/android の写しを includeBuild なし・mavenLocal の 0.4.0 に書き換え（app=com.android.application、data・feature・ui ほか=com.android.library、architecture-test=kotlin("jvm")+katachi、version catalog・configuration cache 有効）。`./gradlew build :architecture-test:katachiDocs` が成功（1 分 49 秒、2 回目 13 秒）。katachiDocs は 33 ページ出力、検査テストは全 PASSED、lint のレポート以外に警告なし。
2. **T2（build-logic の convention plugin）**: T1 の写しに included build の `build-logic`（precompiled script plugin `katachi-architecture` が `kotlin("jvm")` と `id("me.tbsten.katachi")` を適用。katachi-gradle-plugin 0.4.0 を implementation）を足し、architecture-test から `id("katachi-architecture")` で使った。plugin は間接適用でも効いた（`:architecture-test:katachiDocs` 成功、`tasks --all` に katachi のタスクが出る）。`build` は検査テストが「`build-logic` に Role が無い」と `[UnexpectedDirectory]` で落ちた（定義がその directory を知らないので正しい挙動）。
3. **T3（#119 の効き目・AGP 9.1）**: main を専用の `.m2` に publish し、app（com.android.application）と data（com.android.library）に `id("me.tbsten.katachi")` を**誤って**適用した。`:app:katachiProcessors`・`:data:katachiProcessors`・`:app:katachiDocs`・`:app:katachiTemplates`・`:app:katachiInternalDefinitionJson` は、JVM を起動する前に「`<task> has no classpath to run on: me.tbsten.katachi runs on the test runtime classpath of the java plugin, and this module does not apply it (a Kotlin Multiplatform or Android module does not). Apply me.tbsten.katachi to a kotlin("jvm") module that holds the definition and its test, then run <task> there.`」で止まる（Could not find or load main class は出ない）。同じ module の `:app:assembleDebug` は成功し、`:app:tasks --all` に katachi のタスク（Docs・Diff・Template・Templates・Processors・内部 2 つ）が普通に出る（設定時には止まらない）。configuration cache 有効の 2 回目も同じメッセージ。`./gradlew katachiDocs --continue`（モジュールを指定しない）は app・data が FAILED、architecture-test は成功。
4. **T5（AGP 8.9.3 + Gradle 8.13 + Groovy の settings.gradle / build.gradle + `org.jetbrains.kotlin.android` 2.2.21）**: 手で作った app・lib の 2 module に `katachi doctor`（全 OK）→ `KATACHI_MAVEN_LOCAL=1 katachi scaffold --package com.example`（Groovy の settings.gradle に `include ':architecture-test'` と mavenLocal() を 2 か所足せた）→ `agent test`（空の定義で `9 violations (Unexpected: 9)`、想定どおり）。`./gradlew build -x :architecture-test:test` と `--configuration-cache` の 2 回目（`Reusing configuration cache`）、`:architecture-test:katachiDocs`（CC 有効で 2 回、2 回目は reuse）が成功。最初の `build` は手で作った app・lib の JVM target 不一致（AGP 8 + Kotlin Android の定番）で落ちたが katachi とは無関係（`kotlin { jvmToolchain 17 }` で解消）。
5. **T6（AGP 9.1 の KMP library module `com.android.kotlin.multiplatform.library` に katachi を誤適用）**: `:shared:katachiDocs` は #119 のメッセージで止まる。`:shared:assemble`・`:shared:jvmTest` は成功し、`tasks --all` に katachi のタスクが出る。

### 見つけたもの（見出しだけ。同じ症状は書かない）
- [p3] gradle-plugin: 内部タスク `katachiInternalDefinitionJson` の失敗が、利用者向けの案内（「run katachiInternalDefinitionJson there」）で出る
- [p2] gradle-plugin: モジュールを指定しない `./gradlew katachiDocs` は、誤適用した module が先にあると検査用 module まで届かない（--continue 無し）

### 触れなかったところ（ここを優先してよい）
- 実機・エミュレータでの動作（Android の test は assemble・jvmTest・ユニットテストまで。`connectedAndroidTest` は触らない）
- AGP 9.1 の KMP `androidLibrary` の module に定義 module を同居させた形（katachi は kotlin("jvm") の module でしか動かないので意味が無い）
- Gradle 8.0 の古い AGP（AGP 7.x）・Kotlin 2.0/2.1（katachi は Kotlin 2.2 以上が前提）
- `buildSrc` 形・composite build の別 root（クラウドの回に任せた）
- IDE プラグインが誤適用した module（T3・T6 の写し）を開いたときの見え方（B で試す）
