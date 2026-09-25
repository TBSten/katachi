# sample/android

## どういうサンプルか

Jetpack Compose で書いた **マルチモジュールの Android アプリ**を、katachi で定義したサンプルです。

`:feature:*`・`:ui`・`:data`・`:navigation`・`:testing`・`:app` の各モジュールに何を置くかを役割として宣言し、
`assert()` でファイルの配置を検査しています。同じ定義から [`docs/`](docs/README.md) のドキュメントも生成しています。

katachi はリポジトリのソースから `includeBuild("../..")` で取り込んでいますが、書き方は利用者と同じ
`testImplementation(libs.katachi)` です。

## キーとなるファイル

| ファイル                                                                                                        | 何が分かるか                                                                                            |
|-----------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| [`ProjectArchitecture.kt`](architecture-test/src/test/kotlin/com/example/sample/ProjectArchitecture.kt)         | `architecture { }` の本体。group を呼んでいるだけで、役割は `roles/` に1ファイル1役割で置いてある       |
| [`roles/ScreenRole.kt`](architecture-test/src/test/kotlin/com/example/sample/roles/ScreenRole.kt)               | 役割1つの書き方。`:feature:*` の各モジュールに `<Name>Screen.kt` を置く、を `layout { }` で宣言している |
| [`ProjectArchitectureTest.kt`](architecture-test/src/test/kotlin/com/example/sample/ProjectArchitectureTest.kt) | 利用者が書くテストはこれ1つ。`projectArchitecture.assert()` を呼ぶだけ                                  |
| [`docs/README.md`](docs/README.md)                                                                              | 定義から生成したドキュメント。手では書いていない                                                        |

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
```

リポジトリのルートからは、CI と同じ一式を1コマンドで回せます。

```sh
./gradlew checkSampleAndroid
```
