# katachi

**Android / KMP プロジェクトのアーキテクチャを Kotlin DSL で書き、同じ定義から「テスト」と「ドキュメント」の両方を出す**ためのライブラリ。

v0.1 は **Deny by default のアーキテクチャテスト**として出す。
「どの役割のファイルをどこに置けるか」を1箇所に宣言し、宣言に載っていないファイル（`Unexpected`）と、
宣言されているのに実体が無いもの（`Missing`）をテストで検出する。
ドキュメント生成は v0.3 の予定。

> [!WARNING]
> **まだ実装中。** 現時点で動くのは v0.1 のステップ1（`architecture { }` / group / 役割 / `layout { }` の保持）まで。
> `layout { }` の中身の評価も `Architecture.assert()` もまだ無い。公開もしていない（`0.1.0-SNAPSHOT`）。
> 詳細な計画は `.local/features-by-version/v0.1/`（リポジトリには含めていない作業メモ）にある。

## 書き味

```kotlin
// test sourceSet に置く
val projectArchitecture = architecture {
    "domain".group {
        title = "ドメイン"
        "UseCase" {
            title = "ユースケース"
            summary = "各画面で発生するアプリ固有の1つの振る舞い"
            example("GetUserUseCase", "ユーザーを取得する")
            layout { /* ステップ2 で実装 */ }
        }
    }
}
```

- Gradle plugin は要らない。`testImplementation` を足すだけ
- JUnit4 / JUnit5 / kotest のどれでも使える（将来の `assert()` は `AssertionError` を投げるだけ）
- 定義が大きくなったら `ArchitectureScope` の拡張関数に切り出してファイル分割できる
  （分割に使う関数を `inline` にしないこと。`inline` にすると、違反メッセージが示す宣言位置が
  呼び出し元ファイルの末尾より後ろの、存在しない行を指す）

## モジュール構成

| モジュール | 内容 |
|---|---|
| `:katachi` | 本体。**依存ゼロ・JVM only**。座標は `me.tbsten.katachi:katachi` |
| `katachi-konsist` | `konsist { }` 用の任意モジュール。**まだ無い**（v0.1 ステップ4） |

ルートプロジェクトは**サンプルの集約専用**で、プラグインもソースも持たない。
`./gradlew check` が Android SDK や Kotlin/Native ツールチェーン無しで通る状態を保つため。

## サンプル

`sample/` の下に3つ置いてある。どれも `settings.gradle.kts` と gradle wrapper を自前で持つ
**独立した Gradle ビルド**で、`includeBuild("../..")` で katachi をこのリポジトリのソースから取り込む。
利用者と同じ書き方（`testImplementation(libs.katachi)`）で使うので、結合テストを兼ねている。

| サンプル | 内容 | ルートから回すタスク |
|---|---|---|
| `sample/jvm` | Ktor の最小サーバ | `./gradlew checkSampleJvm` |
| `sample/android` | マルチモジュールの Android アプリ | `./gradlew checkSampleAndroid` |
| `sample/kmp` | Android + iOS の KMP プロジェクト | `./gradlew checkSampleKmp` |

```bash
./gradlew check         # katachi 本体（:katachi）のテスト
./gradlew checkSamples  # 3サンプルすべて。各サンプルの gradlew を順に叩く
```

`checkSamples` は3サンプルを**順番に**回す（3つとも同じ katachi ビルドを共有していて、
並行させると katachi の `build/` が壊れるため）。個別に回したいときは各サンプルのディレクトリで
そのサンプルの `./gradlew` を直接叩いてもよい。

回すタスクは `-Pkatachi.sample.<name>.task=...`（全サンプルなら `-Pkatachi.sample.task=...`）で差し替えられる。
`sample/kmp` だけは `check` ではなく `:app:android:testDebugUnitTest` が既定になっている。
`check` は iOS ターゲットのコンパイルを task graph に入れてしまい、Linux では通らないため。

### Android SDK

`sample/android` と `sample/kmp` は Android SDK を要求する。次のどちらかを用意する。

- 環境変数 `ANDROID_HOME`（または `ANDROID_SDK_ROOT`）を設定する — CI はこちら
- `sample/android/local.properties` / `sample/kmp/local.properties` に `sdk.dir=...` を書く
  （`local.properties` はマシン固有なのでコミットしない）

どちらも無い場合、ルートの `checkSample*` タスクは Android Studio の既定の SDK 位置
（`~/Library/Android/sdk` / `~/Android/Sdk`）を最後の手段として探す。

## 開発

| もの | バージョン |
|---|---|
| Gradle | 9.6.0 |
| Kotlin | 2.4.10 |
| JDK / toolchain | 21 |
| kotest | 6.2.5 |
| AGP（サンプル） | 9.4.1 |

- Kotlin / katachi / kotest のバージョンは `gradle/libs.versions.toml` が SSoT。
  3サンプルはこれを `libs` として読み、サンプル固有の依存（Ktor / AGP）だけを自分の catalog（`sampleLibs`）に持つ
- AGP 9 は Kotlin コンパイラを内蔵していて、放っておくと katachi より古い Kotlin でサンプルをコンパイルしてしまう。
  サンプルのルート `build.gradle.kts` がその版を引き上げている（理由はそのファイルのコメントに書いてある）。
  CI は `.github/scripts/check-kotlin-versions.sh` で、この回避策が効き続けているかを毎回突き合わせる
- CI は `.github/workflows/ci.yml`。`main` への push と pull request で、本体と3サンプルをそれぞれ別ステップで回す
