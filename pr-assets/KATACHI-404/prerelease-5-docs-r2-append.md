
## [p6] katachi-script のページに、コマンドをスクリプトのあるディレクトリで実行することが書かれていない
- file: ja/guides/cli-and-script/katachi-script.mdx
- line: 37
- quote: katachi test.katachi.kts
- impact: あり: CI 用に `scripts/` などへ置いたスクリプトをプロジェクトのルートから実行すると、コマンドが `scripts/` で動き、「定義のディレクトリがありません」など別の原因を指すエラーになる
- recommend: 直してもらう 8, 直さない 2

- 該当: `KatachiScript.cwd` の既定はスクリプトのあるディレクトリ（KDoc にだけある）。このページと `katachi --help` には無い。`cwd = Path.of("..")` のような相対パスはシェルのカレントから解決され、スクリプト自身のファイル操作もシェルのカレントが基準なので、1 つのスクリプトに基準が 2 つある。シェルのカレントで動かすなら `cwd = null`
- 再現: `mkdir scripts && echo 'katachi.lint.execute()' > scripts/lint.katachi.kts && katachi scripts/lint.katachi.kts` → `[x] 定義のディレクトリがありません: …/scripts/architecture-test/src/test/kotlin`（ルートで `katachi lint` なら通る）
- 直し方の案: 実行方法の直後に「コマンドはスクリプトのあるディレクトリで実行されます。シェルのカレントディレクトリで動かすなら `cwd = null` と書きます」を 1 文足す。en も同じ位置に訳す。既定そのものをシェルのカレントに変える案は方針が割れるので、コードの側で別に考える
- 出どころ: 探索的検証 2 周目 CLI/script（p6）

## [p4] katachi-script の表の「DependsOn, import: 不要」が、*.katachi.kts では @file:DependsOn を使えないことを伝えない
- file: ja/guides/cli-and-script/katachi-script.mdx
- line: 109
- quote: | DependsOn, import | 不要 | 必要 |
- impact: あり: ログの解析などで別のライブラリを使いたい人が `*.katachi.kts` に `@file:DependsOn` を書くと `unresolved reference 'DependsOn'` で止まり、`*.main.kts` に書き直すことになる
- recommend: 直してもらう 7, 直さない 3

- 該当: `*.katachi.kts` は `@file:DependsOn`・`@file:Repository` を受けない。KDoc は「A `*.katachi.kts` is a `.main.kts` written with [Katachi] minus the preamble」で、`.main.kts` と同じに使えるように読める
- 再現: `printf '@file:DependsOn("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1")\nprintln("ran")\n' > dep.katachi.kts && katachi dep.katachi.kts` → `error: unresolved reference 'DependsOn'.`（終了コード 1）
- 直し方の案: 表の `*.katachi.kts` の欄を「不要（使えません。別のライブラリが要るなら `*.main.kts`）」にする。en も
- 出どころ: 探索的検証 2 周目 CLI/script（p4）

## [p5] diff-report の「CI に組み込む」の workflow に concurrency が無く、push が続くと古い head のレポートだけが残る
- file: ja/guides/diff-report.mdx
- line: 255
- quote: name: Diff report
- impact: あり: 写した workflow で push が続くと、後から終わった古い head の run が新しいレポートを消して古いレポートだけが残り、レビュアーが古いレポートを最新だと思って読む。2 つの run が同じコメントを消しにいくと job も赤くなる
- recommend: 直してもらう 8, 直さない 2

- 該当: 例の workflow に `concurrency` が無い。本リポジトリの `.github/workflows/diff-report.yml` には `concurrency:`（`group: ${{ github.workflow }}-${{ github.event.pull_request.number }}`・`cancel-in-progress: true`）がある
- 直し方の案: `on:` の下に本リポジトリと同じ `concurrency` を足す。en も同じ位置に。action の側（`replace-comment.sh` が DELETE の 404 を「もう消えている」として続ける）はコードの直しで別
- 出どころ: 探索的検証 2 周目 diff（p5）

## [p5] SARIF を Code scanning に上げる手順がサイトのどこにも無い
- file: ja/guides/baseline.mdx
- line: 97
- quote: SARIF のレポート（`report { sarif = true }`）にも書かれません。
- impact: あり: `report { sarif = true }` を有効にしても、ファイルの場所（`build/reports/katachi/katachi.sarif`、同じ JVM の 2 回目は `katachi-2.sarif`、test 以外の Test タスクは `<task>/katachi.sarif`）と upload-sarif に要るもの（`security-events: write`・`category`・`if: always()`、fork の PR では上げられない）が、plugin の Javadoc と core の KDoc にしか無い
- recommend: 直してもらう 7, 直さない 3

- 該当: サイトで SARIF が出てくるのはこのページ（ja・en）の 1 文だけ。リポジトリの `.github/workflows/ci.yml` のコメントは upload の手順を「recipes/github.mdx が利用者に足させる手順」と説明するが、recipes/github.mdx は `.github/` を宣言するレシピで SARIF の話は無い。CI の手順は `katachi.sarif` 1 つだけを上げる形なので、写すと 2 つ目の architecture test の `katachi-2.sarif` が上がらない
- 直し方の案: recipes/github.mdx（か Gradle plugin のページ）に「SARIF を Code scanning に上げる」節を足す（ファイルの場所・番号つきのファイルはディレクトリごと上げる・`category`・fork の PR）。足さないなら ci.yml のコメントを直す（こちらは CI・開発の直し）
- 出どころ: 探索的検証 2 周目 Gradle plugin（p5）

## [p3] 出力先の例の rootProject.layout が、Gradle の isolated projects では違反になる
- file: ja/guides/document-generation.mdx
- line: 171
- quote: outputDir = rootProject.layout.projectDirectory.dir("docs/architecture")
- impact: あり: isolated projects（incubating）を有効にした人が例を写すと「Project ':arch-b' cannot access 'Project.layout' functionality on another project ':'」で止まる。`..` は plugin が断るので、書ける形を自分で探すしかない
- recommend: 直さない 7, 直してもらう 4

- 該当: isolated projects では別のプロジェクト（ルート）の `layout` に触れない。`isolated.rootProject.projectDirectory.dir("docs/architecture")` や `rootDir.resolve("docs/architecture")` なら書ける。plugin 自身の違反は無い
- 直し方の案: 例の下に「isolated projects を有効にしているなら `isolated.rootProject.projectDirectory.dir(...)`」を 1 文添える。en も。incubating の機能なので、いまは書かない判断もある
- 出どころ: 探索的検証 2 周目 Gradle plugin（p3）
