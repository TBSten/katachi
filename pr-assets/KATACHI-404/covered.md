# covered.md — Claude の探索者が見たところ（v0.4.0 prerelease 5 回目、2026-11-20 22:40 JST 時点）

Codex の広域の遊撃に渡す。ここに無い所を優先する。1 周目は終わり、2 周目の 4 本（core・katachiDiff・CLI/script・Gradle plugin）は実行中（下の「2 周目の進み」は途中のメモ）。
2 周目の残り 4 本（konsist/konture・docs・実在の OSS への導入・v0.3 からの移行）はこれから。

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

### 触れなかったところ
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

### 触れなかったところ
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

### 触れなかったところ
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

### 触れなかったところ
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

### 触れなかったところ
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

### 触れなかったところ
- 日本語 get-started の「AI Agent で導入する」の実行（brew・gh skill・Agent Skill による導入は 9-2 の領分）
- Template・Diff report・CLI・katachi-script・processor のページのコード実行（それぞれ他のチャーター）
- 「通常のブラウザ」以外（Safari・Firefox）、実機のスマホ
- API リファレンスの各シンボルの説明文
- 0.2 / 0.3 の旧版ページ
- ダークモードは 390px の Layout ページ 1 枚の目視のみ（全ページの見た目の崩れは手順 6 の領分）

## 2 周目の進み（実行中）: core-r2

- 12:48 UTC 開始。brief・チャーター・1 周目・decisions を読んだ
- 12:53 b1（1 周目の repro の写し）を作った
- 13:01 run A/B/BL1 済み: 特殊文字（fileName の glob 化・Konsist incomplete・SARIF uri）、ignore の総当たり（wholeTree + **/build が黙って通る）。BL2+C（壊した baseline）実行中
- 13:05 gitTracked: index の状態・submodule・入れ子の repo/worktree・sparse・dubious ownership・PATH に git 無し を確認。lb（Kotlin 2.2.20 + Gradle 8.0）開始
- 13:08 lb: Kotlin 2.2.20 + Gradle 8.0 + JDK 17 で README どおり（-Xcontext-parameters）に通り、違反も落ちる。katachiDocs 実行中。symlink（loop・外向き）は file 扱いで問題なし
- 13:10 big: 2 万ファイルで layout 0.4 秒・ktFileContent 8.4 秒（既定 512MB で足りる）・maxViolations=all で 2 万件は console 13.8MB / XML 28MB でも完走
- 13:14 baseline: 壊した JSON の文面は良い。UnexpectedDirectory の記録が新しいファイルを黙って許す。update を baseline() なしで渡すと黙って無視。Gradle 8.0 + CC も可
- 13:21 repro/ を作り、主な発見の再現を流し始めた（rchain）
- 13:26 追加: 空白・日本語を含む絶対パス、symlink の根、worktree の gitdir 切れ（黙って wholeTree）を確認。これから書く

## 2 周目の進み（実行中）: diff-r2

- 12:48 UTC 開始。brief・チャーター・1 周目・decisions を読む
- 12:52 写し（1 周目の proj を複製）を組み、Gradle の katachiDiff と直接起動の tools/diff.sh を用意（mavenLocal の 0.4.0 = 79ab282a、shellQuote 入り）
- 12:54 #117 の周り: 変な名前 36 個の枝 q。{{DATA}}/{{COMMITS}} を含む名前で HTML のプレースホルダが二重に展開 → コミットの件名で属性注入、autofocus で alert が出た（Playwright、枝 xss）
- 12:56 引用: bash/sh/dash で 36 件の git diff を実行。シェルの注入は無し。glob（* ? []）と先頭 : の pathspec で別のファイルまで出る。Markdown は cmark-gfm（GitHub の描画器）で崩れ無し。Role 名は識別子に限られる（記号は入らない）
- 12:57 名前の変更: -M の境目（50%/40%/60%）・変更と同時・コピーは git と一致
- 12:58 未追跡: UTF-16・8000 バイトより後ろの NUL・空は一致。.gitattributes の binary/-diff は未追跡だけ行数に数える。300MB の 1 行は -Xmx512m で +0（OOM を握りつぶす）、500MB の複数行は 0.2GB で 2 秒
- 12:59 Actions: replace-comment.sh を偽の gh で。0/1/150 件・投稿失敗（前のが残る）は良い。DELETE が 404 だと投稿後に job が落ちる。印で始まる他人のコメントも消す。古い head の run が後に終わると古いレポートだけが残る（状態つきの偽 gh）
- 13:02 working-directory と report-directory: resolve/assemble を写しで再現。report-directory を repo のルートからのパスやファイルで渡すと resolve は成功扱いで、次の手順が『did katachiDiff run?』
- 13:03 大きさ: 2 万ファイルで生成 3 秒・0.6GB、HTML 29.5MB / JSON 9.4MB / YAML 8MB。Chromium で読み込み 9〜21 秒、絞り込み・expand all が 1 回 9〜24 秒（2k:0.3s, 5k:0.9s, 10k:2-3s, 20k:9-14s）。コメントは 64,491 字に切られ GFM として崩れない。JSON と YAML は読み戻して一致
- 13:07 git の形: submodule・symlink・LFS のポインタは素直。削除だけのファイルが no role に入ると『architecture test will fail on them』に数える
- 13:08 shallow clone（before=/pr=）・gh 無し・未認証のメッセージは直し方まで出る（発見なし）
- 13:10 kotlinpoet を clone して定義を書き 2.3.0..2.4.0 / 2.2.0..2.3.0。並びは妥当。ファイルの行は 48 中 43 が 🟨 1 つ。javapoet モジュールの削除 7 ファイルが no role で『test will fail』
- 13:13 assemble-comment の境目を 1512 通り総当たり: 65,536 字（コードポイント）を超えるものは無し・details の対は崩れない。🟥 の行が多いと UTF-16 では 68,481 まで（GitHub の数え方は未確認）
- 13:21 例にない触り方: 名前の変更で Role をまたぐ（Service → Repository）と移動元の Role が Unchanged・changedRoles に出ない。どの Role にも無い場所へ移すと、古い Role の下に ⬜ で出て no role の警告も noRoleFileCount も 0（layout 検査は UnexpectedDirectory で失敗）。ガイドの jq の関門が黙って通る。逆に no role のファイルを消すだけの PR で関門が失敗。出力先の衝突（markdownOutputFile=…html）で HTML が黙って上書き。before/after の取り違えは黙って逆の差分

## 2 周目の進み（実行中）: cli-script-r2

12:48:49 start (r2)
12:50:40 base project ready
12:54:33 #117 opts done: --katachi takes -n/--dryrun/--/' ' and writes; --save -- swallows separator (misleading msg)
12:57:38 fs: huge vendor/.venv 70k files +1.4s only; symlink loops/broken OK; unreadable dir fixed for all cmds (nobody). FOUND unreadable check-list.html below cwd -> all cmds incl --version stacktrace rc=3; unreadable dir in definition dir -> lint stacktrace
13:01:48 scripts: args ok except -J/-D/@ (r1 p6 sibling: -J-version prints JVM ver and skips script rc0); DependsOn unsupported in .katachi.kts; pt scaffolded; first agent test
13:05:35 FOUND concurrency: same-second agent test -> same log file, garbled, A's summary shows B's 'Showing first' (evidence conc3-evidence). next: kotest styles multi-module
13:10:04 kotest styles all 12 compile+run OK. exit codes: agent test rc1 for ambiguous workdir/bad KATACHI_WORKDIR/bad meta.moduleDir (help says 1=check failed); katachi test/init rc1 (usage). next: lang en/ja sweep
13:13:19 doctor: old JDK -> 'そろっています' then [x] JDK 11 rc1 (verdict contradicts); no-git doctor ok; lang en has no JA leftovers. next: scaffold breadth (3a-3e)
13:15:24 script stacktraces clean (file:line); CRLF settings -> mixed LF lines; space-in-path warning is upstream kotlin (plain main.kts same)
13:17:09 s3a FAIL_ON_PROJECT_REPOS / s3b groovy / s3c includeBuild+pluginManagement / s3d catalog inline+kotest all OK (rc1 Unexpected). s3e KMP pending. lint rc0 w/ findings OK
13:18:08 FOUND script in subdir: commands run in the script's dir (KDoc only) -> agent test 'gradlew がありません …/ci/gradlew' rc2
13:19:44 FOUND agent test says '検査は通りました（違反 0 件）' rc0 when test SKIPPED (--dry-run passthrough, @Disabled)
13:21:46 JDK21 agent test; subprocess runner ok; compare-violations 0/1/2 ok; dry-run log counts as 0 violations in compare
13:22:43 FOUND scaffold --module-dir app --force overwrites the app module's build.gradle.kts (help says 'existing verification module'); rich version catalog -> clear die
13:28:54 FOUND scaffold --module-dir static-analysis/katachi -> 次: katachi agent test fails (looks for architecture-test), lint too; no --module-dir on them; fix hint = checklist meta.moduleDir. 120k files: all cmds fast

## 2 周目の進み（実行中）: gradle-plugin-r2

12:49 start (brief・charter・1 周目・decisions を読んだ)
12:52 P: g9.6 + K2.4.10 + kts の土台を作成 (app / architecture-test)
12:54 P 9.6.0 で test・katachiDocs OK（warning なし）。8.5 / 8.14.3 に写した
12:56 8.5 / 8.14.3: test・katachiDocs・tasks を CC + warning-mode all で OK（警告は KGP の Gradle 版のみ）
12:58 Q: ktFileContent の docs が実ファイル名を Example に出す → 名前の変更後 katachiDocs UP-TO-DATE で古い / キャッシュから別の状態が出るか確認中
12:59 Q: 再現 OK。名前の変更後 UP-TO-DATE で古いまま・mode=check は different・出力を消すと FROM-CACHE で存在しないファイル名（p9 候補）
13:00 R: outputDir=dir("docs") で手書き README.md 上書き・adr/*.md 削除 → decisions/docs.md「後で KATACHI-370」に該当。エラーメッセージ・Javadoc・サンプルの dir("docs") の勧めだけ新しい面
13:03 S: 同じ outputDir の 2 モジュールは後勝ちで片方を Removed（毎回入れ替わり・cache は --info だけ）。outputs 宣言の processor は UP-TO-DATE・別モジュールのコード変更で再実行・戻すと FROM-CACHE か確認
13:10 T: わざと間違える（architecture なし・register 2 回・diff/template の両方書き・自作 docs + docs {}・Werror/explicitApi）。U: Groovy の入れ子の型つきブロックが全部 KatachiProcessors に unknown property で落ちる（it. で回避）p7 候補
13:15 V: SARIF の uri は相対 + %SRCROOT% + android/ 接頭辞で良い。ignoreFailures + assert 2 回 + build cache で FROM-CACHE が katachi.sarif だけ戻し katachi-2.sarif が消える（p8 候補）。template 生成は良い（親を作る・/・..・空を止める）
13:17 X: archTest（2 つ目の Test）は違反を足しても UP-TO-DATE で成功（tasks.test にだけ 2 行）。Y: 定義を archTest の source set に置くと build が生成ファイルの 30 行のエラー
13:21 Z: Kotest + baseline.update OK。JDK21+toolchain17 / toolchain なし OK。p-g96 で CC+cache+warning-mode all 2 回ずつ・定義変更で再実行 OK。IP: rootProject.layout が違反（docs の書き方）
13:25 diff の型つき項目は KTS で全部効く。mode=CHECK は processor が断る。outputDir=Directory は別パスの checkout で cache miss（p2）

