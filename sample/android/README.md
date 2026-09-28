# sample/android

## どういうサンプルか

Jetpack Compose で書いた **マルチモジュールの Android アプリ**を、katachi で定義したサンプルです。

`:feature:*`・`:ui`・`:data`・`:navigation`・`:testing`・`:app` の各モジュールに何を置くかを役割として宣言し、
`assert()` でファイルの配置を検査しています。同じ定義から [`docs/`](docs/README.md) のドキュメントも生成しています。

katachi はリポジトリのソースから `includeBuild("../..")` で取り込んでいますが、書き方は利用者と同じ
`testImplementation(libs.katachi)` です。

## キーとなるファイル

| ファイル                                                                                                        | 何が分かるか                                                                                              |
|-----------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| [`ProjectArchitecture.kt`](architecture-test/src/test/kotlin/com/example/sample/ProjectArchitecture.kt)         | `architecture { }` の本体。group を呼んでいるだけで、役割は `roles/` に1ファイル1役割で置いてある         |
| [`roles/ComponentRole.kt`](architecture-test/src/test/kotlin/com/example/sample/roles/ComponentRole.kt)         | 役割1つの書き方。置き場所の `layout { }` と、そこへ `App<Name>.kt` を生成する `template { }` を並べている |
| [`ProjectArchitectureTest.kt`](architecture-test/src/test/kotlin/com/example/sample/ProjectArchitectureTest.kt) | 利用者が書くテストはこれ1つ。`projectArchitecture.assert()` を呼ぶだけ                                    |
| [`docs/README.md`](docs/README.md)                                                                              | 定義から生成したドキュメント。手では書いていない                                                          |
| [`katachi-baseline.json`](katachi-baseline.json) | baseline の台帳。`HomeFormatter.kt` と `:data` の `legacy/` の2件を意図的に残して棚上げしている（[`../README.md`](../README.md#baseline)） |

## 実行方法

**Android SDK が必要です。**`ANDROID_HOME` を設定するか、`local.properties` に `sdk.dir` を書いてください。

```sh
# リポジトリのルートで
echo "sdk.dir=$HOME/Library/Android/sdk" > sample/android/local.properties
```

```sh
cd sample/android

# ファイルの配置を検査する
./gradlew :architecture-test:test

# 定義からドキュメントを docs/ に生成する
./gradlew :architecture-test:katachiDocs

# Component のテンプレートから :ui の component package に AppLabel.kt を生成する
./gradlew :architecture-test:katachiTemplate --arg roleName=Component --arg name=Label

# FeatureComponent のテンプレートから :feature:home の component package に HomeUserCard.kt を生成する
# （feature は layout で :feature:* に付けた名前。どのモジュールに生成するかをこれで選ぶ）
./gradlew :architecture-test:katachiTemplate --arg roleName=FeatureComponent --arg feature=home --arg name=UserCard
```

リポジトリのルートからは、CI と同じ一式（テンプレートから生成 → 検査 → 生成物を削除、まで含む）を1コマンドで回せます。

```sh
./gradlew checkSampleAndroid
```
