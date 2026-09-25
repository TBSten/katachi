# sample/kmp

## どういうサンプルか

**Kotlin Multiplatform のアプリ**（Android と iOS）を、katachi で定義したサンプルです。

`:feature:*`・`:ui`・`:data`・`:navigation`・`:testing`・`:app:android` の各モジュールに何を置くかを役割として宣言し、
`assert()` でファイルの配置を検査しています。`commonMain` の `expect` と `androidMain` / `iosMain` の `actual` のように、
**ソースセットごとに置き場所が分かれるもの**の書き方が見どころです。同じ定義から [`docs/`](docs/README.md) のドキュメントも生成しています。

katachi はリポジトリのソースから `includeBuild("../..")` で取り込んでいますが、書き方は利用者と同じ
`testImplementation(libs.katachi)` です。

## キーとなるファイル

| ファイル                                                                                                                       | 何が分かるか                                                                                      |
|--------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| [`ProjectArchitecture.kt`](architecture-test/src/test/kotlin/com/example/kmp/ProjectArchitecture.kt)                           | `architecture { }` の本体。group を呼んでいるだけで、役割は `roles/` に1ファイル1役割で置いてある |
| [`roles/PlatformImplementationRole.kt`](architecture-test/src/test/kotlin/com/example/kmp/roles/PlatformImplementationRole.kt) | `expect` / `actual` をソースセットごとに宣言した役割                                              |
| [`roles/XcodeProjectRole.kt`](architecture-test/src/test/kotlin/com/example/kmp/roles/XcodeProjectRole.kt)                     | Gradle の管理外にある `app/ios` を、検査しない場所として宣言した役割                              |
| [`ProjectLayoutSpec.kt`](architecture-test/src/test/kotlin/com/example/kmp/ProjectLayoutSpec.kt)                               | 配置の検査。`projectArchitecture.assert()` を呼ぶだけ                                             |

## 実行方法

**Android SDK が必要です。**`ANDROID_HOME` を設定するか、`local.properties` に `sdk.dir` を書いてください
。

```sh
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

```sh
cd sample/kmp

# ファイルの配置を検査する
./gradlew :architecture-test:test

# 定義からドキュメントを docs/ に生成する
./gradlew :architecture-test:runKatachiProcessor --processor=docs
```

タスクは `:architecture-test:test` のように**モジュールのパスまで書いてください。**`test` とだけ書くと
iOS のモジュールのタスクまで対象になり、macOS と Xcode が必要になります。

リポジトリのルートからは、CI と同じ一式を1コマンドで回せます。

```sh
./gradlew checkSampleKmp
```
