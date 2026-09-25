# sample/custom-processor

## どういうサンプルか

**ArchitectureProcessor を自分で書く方法**だけを見せるサンプルです。

ほかの3つのサンプルは「プロジェクトを katachi でどう定義するか」を見せます。こちらは「定義を読んで何かをする processor をどう書くか」を見せます。
そのためアプリ本体は、processor が読む対象として置いてあるだけの小さなものです。読む価値があるのは
`architecture-test/src/test/kotlin/com/example/processors/` の 3 本と、その登録です。

## キーとなるファイル

| ファイル                                                                                                       | 何が分かるか                                                                       |
|----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [`processors/RoleFileCount.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleFileCount.kt)     | 引数を取らない、いちばん単純な processor。役割ごとのファイル数を数える             |
| [`processors/RoleTable.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleTable.kt)             | `--arg` で型のついた引数（`String` / `List` / `Int` / `enum`）を受け取る processor |
| [`processors/RoleDocCoverage.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleDocCoverage.kt) | 検査する processor。`summary` か `example` の無い役割があると run を失敗させる     |
| [`architecture-test/build.gradle.kts`](architecture-test/build.gradle.kts)                                     | 3 本の登録と、モジュールの既定引数 `arg("sortBy", "Name")`                         |

## 実行方法

Android SDK は要りません。JDK 17 だけで動きます。

```sh
cd sample/custom-processor

# ファイルの配置を検査する
./gradlew :architecture-test:test

# 自作の processor を1つずつ実行する
./gradlew :architecture-test:runKatachiProcessor --processor=roleFileCount
./gradlew :architecture-test:runKatachiProcessor --processor=roleTable --arg groups=core,testing --arg sortBy=Declaration
./gradlew :architecture-test:runKatachiProcessor --processor=roleDocCoverage
```

`--arg sortBy=Declaration` は、`build.gradle.kts` に書いた既定値 `Name` より優先されます。

リポジトリのルートからは、CI と同じ一式を1コマンドで回せます。

```sh
./gradlew checkSampleCustomProcessor
```
