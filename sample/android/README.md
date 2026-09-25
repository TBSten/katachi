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

## 実行方法

**Android SDK が必要です。**`ANDROID_HOME` を設定するか、`local.properties` に `sdk.dir` を書いてください。

```sh
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

```sh
cd sample/android

# ファイルの配置を検査する
./gradlew :architecture-test:test

# 定義からドキュメントを docs/ に生成する
./gradlew :architecture-test:runKatachiProcessor --processor=docs

# Component のテンプレートから :ui の component package に AppLabel.kt を生成する
./gradlew :architecture-test:runKatachiProcessor --processor=template --arg roleName=Component --arg name=Label
```

リポジトリのルートからは、CI と同じ一式（テンプレートから生成 → 検査 → 生成物を削除、まで含む）を1コマンドで回せます。

```sh
./gradlew checkSampleAndroid
```
