# katachi の開発に参加する

katachi 自体を直す人向けの情報。使い方は [README](README.ja.md) と[ドキュメントサイト](https://tbsten.github.io/katachi/ja/)にある。

## 使っているもの

| もの | バージョン |
|---|---|
| Gradle | 9.6.0 |
| Kotlin | 2.4.10（IDE プラグインだけは 2.3.0。自分の catalog に持つ） |
| JDK / toolchain | 17（IDE プラグインだけは 21） |
| kotest | 6.2.5 |

## リポジトリの中身

| 場所 | 内容 |
|---|---|
| `katachi/` / `katachi-konsist/` / `katachi-gradle-plugin/` | 公開する3つのモジュール |
| `architecture-test/` | katachi 自身のアーキテクチャ定義。公開しない |
| `tool/dokka/` | API リファレンスを作る Dokka plugin。公開しない |
| `benchmark/` | JMH のベンチマーク。公開しない。`check` には入らず、`./gradlew :benchmark:jmh` で回す |
| `katachi-intellij-plugin/` | IntelliJ IDEA / Android Studio のプラグイン。独立した Gradle ビルド |
| `sample/` | 独立した Gradle ビルドとして動くサンプル |
| `docs/` | ドキュメントサイト |

## サンプル

`sample/` の下に4つある。それぞれの書き味と実行方法は各 README に、サンプル全体に共通する設計方針とビルド設定は [
`sample/README.md`](sample/README.md) にまとめてある。

| サンプル                                                       | 内容                                                                    |
|----------------------------------------------------------------|-------------------------------------------------------------------------|
| [`sample/jvm`](sample/jvm/README.md)                           | Ktor の最小サーバ                                                       |
| [`sample/android`](sample/android/README.md)                   | マルチモジュールの Android アプリ（Compose / AndroidX の実依存あり）    |
| [`sample/kmp`](sample/kmp/README.md)                           | Android + iOS の KMP プロジェクト（Compose Multiplatform の実依存あり） |
| [`sample/custom-processor`](sample/custom-processor/README.md) | 自分で processor を書くときの見本                                       |

4つとも、プラグインと `gradle()` を使い、`katachiDocs` で生成したドキュメントをコミットしている。`.template { }` と baseline を使っているのは jvm / android / kmp だけ。

## 検査する

```shell
./gradlew check          # 本体の検査。Android SDK も Kotlin/Native も要らない
./gradlew checkSamples   # 全サンプルを、それぞれの wrapper で順に回す
./gradlew checkSampleJvm # 1つだけ回す（Android / KMP のサンプルは Android SDK が要る）
./gradlew checkIdePlugin # IDE プラグインの buildPlugin test verifyPreview（JDK 21 が要る）
```

- サンプルはルートのサブプロジェクトではなく独立したビルドなので、`./gradlew check` には入らない。ルートプロジェクトはソースを持たず、API リファレンスの集約とサンプルを回すタスクだけを持つ
- IDE プラグインも独立したビルドで、`check` にも `checkSamples` にも入らない。初回は IntelliJ Platform の SDK（IDE 一式）をダウンロードする。画面の golden（`verifyPreview`）は macOS で作ったもので、ほかの OS ではバイト単位で一致しない。実 IDE を起動するスモーク（`integrationTest`）は手で回す。手元の IDE に入れて試す zip は `./gradlew buildPlugin -Pkatachi.timestampVersion` で作ると版が `0.2.0-261106_1850` のように日付入りになり、同じ版が入っていて Install Plugin from Disk… に断られることがない
- 本体とサンプルの Kotlin / katachi / kotest / JUnit のバージョンは `gradle/libs.versions.toml` が SSoT（IDE プラグインは独立したビルドで、自分の `katachi-intellij-plugin/gradle/libs.versions.toml` を読む。版だけはルートの `katachi` を読み、リリースの zip は `publish.yml` が GitHub Release に添付する）。サンプルはこれを `libs` として読み、サンプル固有の依存は自分の catalog（`sampleLibs`）に持つ（詳細は [`sample/README.md`](sample/README.md)）
- CI は `.github/workflows/ci.yml`。`main` への push と pull request で、本体と4つのサンプルをそれぞれ別のステップで、ドキュメントサイトのビルドを `docs` ジョブで、IDE プラグインを macOS の別のジョブで回す。同じトリガでベンチマークのジョブ（`bench-jmh`・`bench-real-project`・`bench-store`）も回す。結果の比較はジョブのサマリに出すだけで、遅くなっても落ちない。`bench-store` は `main` のときだけ結果を `gh-pages` の履歴に足す

## コードを書く

- Kotlin のコードの書き方は [`docs/internal/kotlin/kotlin.md`](docs/internal/kotlin/kotlin.md)、例外とエラーメッセージは [`docs/internal/kotlin/errors.md`](docs/internal/kotlin/errors.md) に従う

## ドキュメントを書く

- ドキュメントサイトは `docs/`（Astro + Starlight）。先にリポジトリ直下で `./gradlew generateApiDocs` を実行して API リファレンス（`docs/public/api-docs/`。gitignore されており、無いとリンクの検査が落ちる）を用意し、`docs` で `pnpm install` してから `pnpm run build` でビルドできる（リンクの検査を含む）
- **日本語が原本で、英語は翻訳。** `docs/src/content/docs/ja/` と `README.ja.md`・`CONTRIBUTING.ja.md` を書き、英語側はそこから訳す。英語側だけに内容を足さない（詳細は [`docs/CLAUDE.md`](docs/CLAUDE.md)）

## 影響範囲を書く

PR・チケット・調査レポートでは、変更の影響範囲を次の形で書く。読む人が「どこに何が起きたか」を一目でつかむための全体のサマリーで、どのファイル・どの Role が何行変わったかは PR に自動で付く katachiDiff のコメントに任せる（本文では繰り返さない）。

```md
### 影響範囲

#### メインの変更

- CLI
  - new: `katachi <file>.katachi.kts` … スクリプトを実行する
- konsist
  - update!: ktFileContent … enum の項目が deny by default に
    - Before: ``allow(enum, `class`, "Color")``
    - After: ``allow(enum, `class`, "Color") { allow(entry, "*") }``

#### 軽微な変更

- core
  - fix: katachiDiff › ignore() の場所 … no role と数えない

#### テスト・動作確認

- script: `KatachiScriptSpec` 13 件（CI）
- IDE plugin: プラットフォームのテストは CI で初めて走る（手元では未確認）
```

3 段で書く: **変更グループ › コンポーネント › 種類: 変更箇所 … 内容**。見出しの深さは置く場所に合わせてよい（PR テンプレートでは `## 影響範囲` から始まる）。

### 変更グループ

3 つとも見出しを残す。中身が無いグループは消さずに「なし」と書く（書き忘れと区別するため）。

| グループ | 入れるもの |
|---|---|
| メインの変更 | その PR・チケットの目的そのもので、利用者が気づく変更。`!` が付く変更は、小さく見えても必ずここに入れる |
| 軽微な変更 | ついでの修正・文言・内部の整理など、利用者がほぼ気づかない変更 |
| テスト・動作確認 | 足したテストと、どこまで確かめたか（手元・CI・未確認） |

### コンポーネント

名前は次の一覧から選ぶ。「対象」はチケット名 `種別(対象): 内容` の対象に使う言葉。core の「場所」は `katachi/src/main/kotlin/me/tbsten/katachi/` から見たパッケージ。

| コンポーネント | 中身 | 場所 | 対象 |
|---|---|---|---|
| core | katachi 本体のライブラリ | `katachi/` | `core` |
| └ DSL | アーキテクチャ定義を書くための API | `dsl/` | `core` |
| 　└ architecture | `architecture { }`・定義の入口 | `dsl/` | `core` |
| 　└ role・group | Role・group・metadata・naming・example | `dsl/` | `core` |
| 　└ layout | `layout { }`・ファイルの選び方・`gradle()`・module | `dsl/`・`dsl/files/`・`dsl/gradle/` | `core` |
| 　└ fileConstraint | ファイルの中身への制約（konsist・konture が乗る土台） | `dsl/` | `core` |
| 　└ template | `.template { }`・template set | `dsl/` | `core` |
| └ 検査 | assert・validate・違反の形・Severity・layout の警告 | `check/` | `core` |
| 　└ baseline | baseline の読み書き・古くなったエントリ | `check/`・`dsl/` | `core` |
| 　└ SARIF | SARIF レポート | `report/` | `core` |
| └ processor API | `ArchitectureProcessor`・processor の引数・組み合わせ・実行 | `processor/` | `core` |
| └ IDE 連携 | IDE plugin が読む定義 JSON | `ide/` | `core` |
| └ built-in processors | katachi が最初から持つ processor | | |
| 　└ katachiDocs | ドキュメント生成 | `docs/` | `core` |
| 　└ katachiTemplate | テンプレートからのコード生成 | `template/` | `core` |
| 　└ katachiDiff | 差分レポート・PR コメント | `diff/` | `diff` |
| konsist | `ktFileContent`・`konsist { }` | `katachi-konsist/` | `konsist` |
| konture | `konture { }` | `katachi-konture/` | `konture` |
| Gradle plugin | タスク・`katachi { }` | `katachi-gradle-plugin/` | `gradle` |
| CLI | `katachi` コマンド・brew | `katachi-script/cli/` | `cli` |
| script | `*.main.kts` / `*.katachi.kts` の DSL | `katachi-script/src/` | `script` |
| IDE plugin | ガター・ツールウィンドウ・MCP | `katachi-intellij-plugin/` | `ide` |
| docs | ドキュメントサイト・README | `docs/`・`README*.md` | `docs` |
| CI・開発 | workflow・PR テンプレート・CONTRIBUTING・architecture-test・benchmark・`tool/` | `.github/`・`architecture-test/`・`benchmark/`・`tool/` ほか | `ci` |
| sample | サンプル | `sample/` | `sample` |

katachiDocs・katachiTemplate・katachiDiff は core の中の processor なので、コンポーネントとしては core に入れる。core の中の部品は、一覧のいちばん内側の名前を箇所の頭に「›」でつないで書く（例: core の下に `update: katachiDiff › Importance … 基準値 400 行で測る`。`DSL › layout ›` のように外側からたどらなくてよい）。

### 変更の種類

| 種類 | 意味 | 例 |
|---|---|---|
| `new` | 新しくできたこと | CLI / `new: hoge コマンド` |
| `update` | 今ある機能の見た目・挙動・既定値が変わる | core / `update: katachiDiff › Importance … 基準値 400 行で測る` |
| `fix` | バグを直した。正しい挙動に戻るだけ | core / `fix: katachiDiff › ignore() の場所 … no role と数えない` |
| `delete` | 機能・API を消した | core / `delete: layout › .module { } … 非推奨 API を削除` |

利用者のコードや定義を書き換えないとコンパイルが通らない・検査が赤くなる変更は、種類の後ろに `!` を付け（`update!`・`delete!` など）、すぐ下に Before / After を必ず書く。書いた Before / After はそのままリリースノートの移行手順に使う。

### 書く場所

| 場所 | 書き方 |
|---|---|
| PR 本文 | チケットの直後に 3 段の全部（[PR テンプレート](.github/pull_request_template.md)の枠を埋める）。何をしたかの詳しい説明はその後ろに |
| チケット | 起票時は予定の影響範囲を「実装内容概要」に。マージ後は PR から「成果」に写す |
| 調査レポート | 案ごとに「この案を採ったときの影響範囲」を同じ形で並べて比べる |
| チャットの報告 | 1 行に縮める（例: `影響: CLI new / IDE plugin new×2 / konsist update!`） |

リリースノートは、PR の影響範囲を集めて `!` → 破壊的変更、`new` → 新機能、`update` → 変更点、`fix` → 修正、とコンポーネントごとに並べれば作れる。
