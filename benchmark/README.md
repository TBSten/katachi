# benchmark

katachi の性能を測るモジュール。公開しない。`check` には入らず、CI の `bench-jmh`・`bench-real-project`（push と pull request ごと）と
`gradle-profiler.yml`（週 1）が回す。回し方は [build.gradle.kts](build.gradle.kts) の先頭のコメントにある。

## 対応表: 何を測っていて、何を測らないか

機能を足しても、ベンチを足し忘れたことはどこにも出ない。そこで「どのコンポーネントを、どのベンチで見ているか」をここに書き、
測らないものは理由を書く。**新しい入口・processor・検査の経路を足したら、ベンチを足すか、この表に測らない理由を書く。**

- 行はコンポーネントごと。名前は [`.github/labels.json`](../.github/labels.json) の `影響:<コンポーネント>` と同じにする
- ベンチの書き方: JMH は class 名（`WalkBench`）、実プロジェクトの計測は `realProject:<名前>`、gradle-profiler は `gradle-profiler:<シナリオ>`。無ければ `なし` と書き、理由を書く
- 「確認した版」は、その行を最後に見直したリリース。まだ人が決めていない行は `未確認`
- 表と実物が合っているかは `.github/scripts/check-benchmark-coverage.py` が CI の `check` で確かめる。行ごとの見直しは、リリース前チェック（prerelease の手順 12）でする

| コンポーネント | 入口 | 場所 | ベンチ | 測らない理由 | 確認した版 |
|---|---|---|---|---|---|
| core/DSL | `architecture { }` の定義を layout の行に開く・ファイルを role に結び付ける | `katachi/src/main/kotlin/me/tbsten/katachi/dsl` | `LayoutEvalBench`・`RoleMatchBench` | | v0.4.0 |
| core/検査 | `validate()`・`assert()` | `katachi/src/main/kotlin/me/tbsten/katachi/check`・`katachi/src/main/kotlin/me/tbsten/katachi/report` | `WalkBench`・`AssertColdBench`・`realProject:nowinandroid`・`gradle-profiler:architectureTest` | | v0.4.0 |
| core/processor API | processor の実行 | `katachi/src/main/kotlin/me/tbsten/katachi/processor` | なし | processor を呼ぶ枠だけで、時間は各 processor の中身と JVM の起動が主。Gradle から呼んだときの待ち時間は `gradle-profiler:katachiDocs` が見る | v0.4.0 |
| core/IDE 連携 | IDE plugin と `katachi lint` が読む定義 JSON の書き出し | `katachi/src/main/kotlin/me/tbsten/katachi/ide` | なし | 定義を 1 回書き出すだけ。JMH の外で、IDE の側のテストは別にある | v0.4.0 |
| core/katachiDocs | `katachiDocs` | `katachi/src/main/kotlin/me/tbsten/katachi/docs` | `gradle-profiler:katachiDocs` | | v0.4.0 |
| core/katachiTemplate | `katachiTemplate` | `katachi/src/main/kotlin/me/tbsten/katachi/template` | なし | 1 回の操作で、待ち時間は JVM の起動が主（`gradle-profiler:katachiDocs` と同じ経路） | v0.4.0 |
| core/katachiDiff | `katachiDiff` | `katachi/src/main/kotlin/me/tbsten/katachi/diff` | なし | 1 PR に 1 回の実行で、時間の大半は git の呼び出し。role への割り当ては walk と同じ索引で、`WalkBench`・`RoleMatchBench` が見ている | v0.4.0 |
| konsist | `konsist { }`・`ktFileContent { }`・`FileConstraintCheck()` | `katachi-konsist/src/main` | `KonsistScopeBench`・`KtFileContentBench`・`AssertColdBench`・`realProject:nowinandroid` | | v0.4.0 |
| konture | `konture { }` | `katachi-konture/src/main` | `KontureBench`・`AssertColdBench` | | v0.4.0 |
| Gradle plugin | `:architecture-test:test`・`katachi<Key>` のタスク | `katachi-gradle-plugin/src` | `gradle-profiler:architectureTest`・`gradle-profiler:katachiDocs` | | v0.4.0 |
| Actions | `diff-report`・`diff-report-comment` | `diff-report`・`diff-report-comment` | なし | `katachiDiff` を呼んで結果を貼るだけ（core/katachiDiff の行を見る） | v0.4.0 |
| CLI・script | `katachi <サブコマンド>`・`*.katachi.kts` | `katachi-script` | なし | 起動は `kotlin` コマンドのスクリプトのコンパイルと依存の解決が主で、JMH には載らない。`katachi lint` は `./gradlew` で定義を評価するので、待ち時間は Gradle の起動と定義のコンパイルが主で、JSON を 1 回読む lint 自身は測るほどではない。測るなら別の仕組みが要る | 未確認 |
| IDE plugin | ガター・ツールウィンドウ・MCP | `katachi-intellij-plugin/src` | なし | JMH の外。IDE の側のテスト（uiTest・integrationTest）が別にある | v0.4.0 |
| skill | `skills/katachi/` | `skills/katachi` | なし | 文書だけで、実行するコードが無い | v0.4.0 |
| docs | ドキュメントサイト・README | `docs` | なし | katachi の実行時間に関わらない | v0.4.0 |
| CI・開発 (ユーザ影響無し) | workflow・architecture-test・benchmark・`tool/` | `.github`・`architecture-test`・`benchmark`・`tool` | なし | 測る側の道具で、利用者の手元では動かない | v0.4.0 |
| sample | サンプル | `sample` | なし | katachi を使う側の例。`sample/jvm` は gradle-profiler が測る土台に使う | v0.4.0 |
