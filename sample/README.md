# sample

## これは何か

`sample/` の下に4つの独立した Gradle ビルドが置いてある。どれも自前の `settings.gradle.kts` と
gradle wrapper を持ち、`includeBuild("../..")` で katachi をこのリポジトリのソースから取り込む。
利用者と同じ書き方（`testImplementation(libs.katachi)`）で使うので、結合テストを兼ねている。

| サンプル | 内容 | katachi の定義の置き場所 | ルートから回すタスク |
|---|---|---|---|
| [`jvm`](jvm/README.md) | Ktor の最小サーバ（アプリ本体はサンプルの1モジュール） | `:architecture-test` | `./gradlew checkSampleJvm` |
| [`android`](android/README.md) | マルチモジュールの Android アプリ（Compose / AndroidX の実依存あり） | `:architecture-test` | `./gradlew checkSampleAndroid` |
| [`kmp`](kmp/README.md) | Android + iOS の KMP プロジェクト（Compose Multiplatform の実依存あり） | `:architecture-test` | `./gradlew checkSampleKmp` |
| [`custom-processor`](custom-processor/README.md) | 利用者が自分で書く processor 3本の見本。アプリ本体は3ファイルだけ | `:architecture-test` | `./gradlew checkSampleCustomProcessor` |

どのサンプルも `:architecture-test` はルートの README「導入」で書いた推奨形そのもので、`kotlin("jvm")` と
`testImplementation(libs.katachi)` しか持たない。

## 実物に近い中身にしてある理由

スタブではなく**実物に近い中身**にしてある。`android` / `kmp` の `Screen` は本物の `@Composable`、
`ViewModel` は本物の `androidx.lifecycle.ViewModel` を継承し、`@Preview` も実際に書いてある。
検査対象が実プロジェクトと同じ形でなければ、katachi が実際の構成で機能することを確かめたことに
ならないため。

## モジュールの切り方

`android` / `kmp` はモジュールの切り方も実プロジェクト寄りにしてある。`:ui` と `:data` は
**1モジュールの中を package で分ける**形にした。

| モジュール | android | kmp |
|---|---|---|
| `:ui` | `component` / `theme` / `core` / `preview` | 同じ |
| `:data` | `user` / `settings` | `user` / `platform` |

katachi が表現できなければならない形の中で最もよく出てくるのがこれなので、この2サンプルの
主眼はここにある。

## 定義のファイル分割

各サンプルの `architecture { }` は1ファイルではなく、`groups/`（1ファイル1 group）と
`roles/`（1ファイル1役割）に package を分けて書いてある。それぞれが非 inline の `ArchitectureScope`
拡張関数を公開し、`ProjectArchitecture.kt` はそれを呼ぶだけ。**拡張関数に切り出しても宣言位置が
呼び出し元ではなく定義を書いたファイルを指すこと**を、各サンプルの `ProjectArchitectureSpec` が
ファイル名の完全一致で検証している。

## baseline（意図的に残した違反）

`jvm` / `android` / `kmp` は定義に `baseline = baselineFile()` を書き、ルート直下の
`katachi-baseline.json` に「katachi を入れた時点ですでにあった違反」を記録して棚上げしている。
台帳が空では何も確かめられないので、**違反を意図的に残してある**（`custom-processor` は対象外）。
どれもソースのコメントに「baseline のデモとして意図的に違反している」と書いてある。

| サンプル | 残してある違反 |
|---|---|
| `jvm` | `service/LegacyHealthCheck.kt`（`[UnexpectedFile]`）、`service/LegacyStatusService.kt`（`internal` なので `konsist { }` の制約違反） |
| `android` | `:feature:home` の `HomeFormatter.kt`（`[UnexpectedFile]`）、`:data` の `legacy/`（`[UnexpectedDirectory]`） |
| `kmp` | `:data` の `androidMain` にある `user/`（`[UnexpectedDirectory]`） |

`checkSample<Name>` はふだんの検査に加えて、baseline について次の3つを確かめる。

| タスク | 確かめること |
|---|---|
| `checkSample<Name>BaselineHeldBack` | 定義を検査するテストが緑で、`held back N violations.` を出す |
| `checkSample<Name>BaselineUpToDate` | `CI` を外して `-Dkatachi.baseline.update=true` で走らせても、台帳が1文字も変わらない（台帳が最新で、書き出しが決定的） |
| `checkSample<Name>BaselineStale` | 台帳に存在しないファイルの項目を1行足すと `[StaleBaselineEntry]` で落ちる。台帳はそのあと元に戻す |

残した違反を直したり増やしたりしたら、そのサンプルで
`./gradlew :architecture-test:test -Dkatachi.baseline.update=true` を走らせて台帳を更新し、
ルートの `build.gradle.kts` の `SampleBaseline(heldBack = ...)` の件数を合わせる。

## checkSamples を順番に回す理由

```bash
./gradlew check         # katachi 本体（:katachi）のテスト
./gradlew checkSamples  # 全サンプル。各サンプルの gradlew を順に叩く
```

`checkSamples` は全サンプルを**順番に**回す。どのサンプルも `includeBuild("../..")` で同じ
katachi ビルドを共有していて、並行させると katachi の `build/` が壊れるため。個別に回したいときは
各サンプルのディレクトリでそのサンプルの `./gradlew` を直接叩いてもよい。

## 回すタスクの差し替え

各 `checkSample<Name>` が既定で回すタスクは `-Pkatachi.sample.<name>.task=...`
（全サンプルなら `-Pkatachi.sample.task=...`）で差し替えられる。複数タスクはスペース区切りで書く。
既定値はルートの `build.gradle.kts` の `sampleBuilds` に書いてある。

`kmp` だけは既定タスクが `check` ではない（理由は [`kmp/README.md`](kmp/README.md) 参照）。
`kmp` でタスクを直接指定するときは `:architecture-test:test` のように**モジュールのパスまで書く**
こと。`test` とだけ書くと iOS のモジュールのタスクまで対象になり、macOS と Xcode が必要になる。

## Android SDK

`android` と `kmp` は Android SDK を要求する。次のどちらかを用意する。

- 環境変数 `ANDROID_HOME`（または `ANDROID_SDK_ROOT`）を設定する — CI はこちら
- `sample/android/local.properties` / `sample/kmp/local.properties` に `sdk.dir=...` を書く
  （`local.properties` はマシン固有なのでコミットしない）

どちらも無い場合、ルートの `checkSample*` タスクは Android Studio の既定の SDK 位置
（`~/Library/Android/sdk` / `~/Android/Sdk`）を最後の手段として探す。

## Android サンプルのビルド設定

| もの | バージョン |
|---|---|
| AGP | 9.1.0 — **上げないこと**（下記） |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 |
| Compose | android: BOM 2026.06.01 / kmp: Compose Multiplatform 1.10.3 |

- Kotlin / katachi / kotest のバージョンはルートの `gradle/libs.versions.toml` が SSoT。`android` /
  `kmp` はこれを `libs` として読み、サンプル固有の依存（AGP / Compose ランタイムなど）だけを
  自分の catalog（`sampleLibs`）に持つ
- **Compose コンパイラプラグイン**（`org.jetbrains.kotlin.plugin.compose`）は Kotlin と完全に
  同じバージョンでなければならず、TOML catalog は別の catalog を参照できない。そのためこれだけは
  ルート catalog（`gradle/libs.versions.toml`）に `libs.plugins.kotlinPluginCompose` として置いてある。
  `android` / `kmp` のルート `build.gradle.kts` が `alias(libs.plugins.kotlinPluginCompose) apply false`
  で読む。Compose の**ランタイム**（BOM / Compose Multiplatform）は Kotlin と独立に決まるので、
  そちらはサンプル自身の catalog（`sampleLibs`）のまま
- **AGP は Android Studio 側の対応上限に合わせる。** 9.1.0 なのは、これより新しいと Android Studio の
  Gradle sync が `The project is using an incompatible version (AGP x.y.z) of the Android Gradle plugin.`
  で止まるため。CLI のビルドだけを見て上げると、IDE で開けなくなる。上げるときは
  [Android Studio と AGP の対応表](https://developer.android.com/build/releases/gradle-plugin#updating-gradle)
  を先に見る
- AGP 9 は Kotlin コンパイラを内蔵していて、放っておくと katachi より古い Kotlin でサンプルを
  コンパイルしてしまう。`android` / `kmp` のルート `build.gradle.kts` がその版を引き上げている
  （理由はそのファイルのコメントに書いてある）。CI は `.github/scripts/check-kotlin-versions.sh` で、
  この回避策が効き続けているかを毎回突き合わせる
- **Compose / AndroidX は「最新」ではなく「`minCompileSdk` が 36 以下で最新」を選ぶ。**
  AGP 9.1.0 が扱える `compileSdk` は 36 までだが、2026 年後半の AndroidX は `minCompileSdk=37` を
  宣言し始めている。新しい Compose BOM や `lifecycle` / `navigation` / Compose Multiplatform を入れると
  configuration の時点で `requires ... version 37 or later of the Android APIs` で落ちる。同じ BOM でも
  artifact ごとに `minCompileSdk` が違うので、上げるときは
  `unzip -p <artifact>.aar META-INF/com/android/build/gradle/aar-metadata.properties` で
  1つずつ確かめる
