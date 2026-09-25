# sample/kmp

## どういうサンプルか

**Kotlin Multiplatform のアプリ**（Android と iOS）を、katachi で定義したサンプルです。

`:feature:*`・`:ui`・`:data`・`:navigation`・`:testing`・`:app:android` の各モジュールに何を置くかを役割として宣言し、
`assert()` でファイルの配置を検査しています。`commonMain` の `expect` と `androidMain` / `iosMain` の `actual` のように、
**ソースセットごとに置き場所が分かれるもの**の書き方が見どころです。同じ定義から [`docs/`](docs/README.md) のドキュメントも生成しています。

katachi はリポジトリのソースから `includeBuild("../..")` で取り込んでいますが、書き方は利用者と同じ
`testImplementation(libs.katachi)` です。

## キーとなるファイル

| ファイル                                                                                                                       | 何が分かるか                                                                                                        |
|--------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| [`ProjectArchitecture.kt`](architecture-test/src/test/kotlin/com/example/kmp/ProjectArchitecture.kt)                           | `architecture { }` の本体。group を呼んでいるだけで、役割は `roles/` に1ファイル1役割で置いてある                   |
| [`roles/PlatformImplementationRole.kt`](architecture-test/src/test/kotlin/com/example/kmp/roles/PlatformImplementationRole.kt) | `expect` / `actual` をソースセットごとに宣言した役割                                                                |
| [`roles/RepositoryRole.kt`](architecture-test/src/test/kotlin/com/example/kmp/roles/RepositoryRole.kt)                         | 1つの `template { }` でインターフェースと実装の2ファイルを生成する役割。置き場所は2つの `layout` パターンから決まる |
| [`ProjectLayoutSpec.kt`](architecture-test/src/test/kotlin/com/example/kmp/ProjectLayoutSpec.kt)                               | 配置の検査。`projectArchitecture.assert()` を呼ぶだけ                                                               |

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

# Repository のテンプレートから :data の user package に ProfileRepository.kt と ProfileRepositoryImpl.kt を生成する
./gradlew :architecture-test:runKatachiProcessor --processor=template --arg roleName=Repository --arg name=Profile
```

タスクは `:architecture-test:test` のように**モジュールのパスまで書いてください。**`test` とだけ書くと
iOS のモジュールのタスクまで対象になり、macOS と Xcode が必要になります。

リポジトリのルートからは、CI と同じ一式（テンプレートから生成 → 検査 → 生成物を削除、まで含む）を1コマンドで回せます。

```sh
./gradlew checkSampleKmp
```
