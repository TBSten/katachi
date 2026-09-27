# katachi の開発に参加する

katachi 自体を直す人向けの情報。使い方は [README](README.ja.md) と[ドキュメントサイト](https://tbsten.github.io/katachi/ja/)にある。

## 使っているもの

| もの | バージョン |
|---|---|
| Gradle | 9.6.0 |
| Kotlin | 2.4.10 |
| JDK / toolchain | 17（IDE プラグインだけは 21） |
| kotest | 6.2.5 |

## リポジトリの中身

| 場所 | 内容 |
|---|---|
| `katachi/` / `katachi-konsist/` / `katachi-gradle-plugin/` | 公開する3つのモジュール |
| `architecture-test/` | katachi 自身のアーキテクチャ定義。公開しない |
| `tool/dokka/` | API リファレンスを作る Dokka plugin。公開しない |
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

jvm / android / kmp は、プラグイン・`gradle()`・`template { }` を使い、`katachiDocs` で生成したドキュメントをコミットしている。

## 検査する

```shell
./gradlew check          # 本体の検査。Android SDK も Kotlin/Native も要らない
./gradlew checkSamples   # 全サンプルを、それぞれの wrapper で順に回す
./gradlew checkSampleJvm # 1つだけ回す（Android / KMP のサンプルは Android SDK が要る）
./gradlew checkIdePlugin # IDE プラグインの buildPlugin test verifyPreview（JDK 21 が要る）
```

- サンプルはルートのサブプロジェクトではなく独立したビルドなので、`./gradlew check` には入らない。ルートプロジェクトはソースを持たず、API リファレンスの集約とサンプルを回すタスクだけを持つ
- IDE プラグインも独立したビルドで、`check` にも `checkSamples` にも入らない。初回は IntelliJ Platform の SDK（IDE 一式）をダウンロードする。画面の golden（`verifyPreview`）は macOS で作ったもので、ほかの OS ではバイト単位で一致しない。実 IDE を起動するスモーク（`integrationTest`）は手で回す
- Kotlin / katachi / kotest のバージョンは `gradle/libs.versions.toml` が SSoT。サンプルはこれを `libs` として読み、サンプル固有の依存は自分の catalog（`sampleLibs`）に持つ（詳細は [`sample/README.md`](sample/README.md)）
- CI は `.github/workflows/ci.yml`。`main` への push と pull request で、本体と4つのサンプルをそれぞれ別のステップで、IDE プラグインを macOS の別のジョブで回す

## コードを書く

- Kotlin のコードの書き方は [`docs/internal/kotlin/kotlin.md`](docs/internal/kotlin/kotlin.md)、例外とエラーメッセージは [`docs/internal/kotlin/errors.md`](docs/internal/kotlin/errors.md) に従う

## ドキュメントを書く

- ドキュメントサイトは `docs/`（Astro + Starlight）。`cd docs && pnpm run build` でビルドできる（リンクの検査を含む）
- **日本語が原本で、英語は翻訳。** `docs/src/content/docs/ja/` と `README.ja.md`・`CONTRIBUTING.ja.md` を書き、英語側はそこから訳す。英語側だけに内容を足さない（詳細は [`docs/CLAUDE.md`](docs/CLAUDE.md)）
