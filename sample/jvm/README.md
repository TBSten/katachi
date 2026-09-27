# sample/jvm

## どういうサンプルか

Ktor で書いた**単一モジュールの小さな HTTP サーバ**を、katachi で定義したサンプルです。

API・ドメイン・データの各層に何を置くかを役割として宣言し、`assert()` でファイルの配置を検査しています。
1つの役割には `konsist { }` で「public であること」という制約も書いてあります。
定義自身の役割には、親ディレクトリの制約を子ディレクトリに降ろさない `konsist(directOnly = true)` の例もあります。
同じ定義から [`docs/`](docs/README.md) のドキュメントを生成し、定義を読む自作の processor も1つ置いています。

katachi はリポジトリのソースから `includeBuild("../..")` で取り込んでいますが、書き方は利用者と同じ
`testImplementation(libs.katachi)` です。

## キーとなるファイル

| ファイル                                                                                                 | 何が分かるか                                                                                                   |
|----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| [`ProjectArchitecture.kt`](architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt)         | `architecture { }` の本体。7 つの group を呼んでいるだけで、役割は `roles/` に1ファイル1役割で置いてある       |
| [`roles/ServiceRole.kt`](architecture-test/src/test/kotlin/com/example/roles/ServiceRole.kt)             | `layout { }` に加えて `konsist { }` で制約を書いた役割。`template { }` で `*Service.kt` を生成する見本も兼ねる |
| [`roles/ArchitectureDefinitionRole.kt`](architecture-test/src/test/kotlin/com/example/roles/ArchitectureDefinitionRole.kt) | `konsist(directOnly = true)` の例。`com/example` 直下にだけ効き、`groups/`・`roles/` には降りない制約 |
| [`ProjectArchitectureTest.kt`](architecture-test/src/test/kotlin/com/example/ProjectArchitectureTest.kt) | 利用者が書くテストはこれ1つ。`konsist { }` も評価するために `assert(FileConstraintCheck())` を呼んでいる              |
| [`processors/RoleNames.kt`](architecture-test/src/test/kotlin/com/example/processors/RoleNames.kt)       | `--arg` で引数を受け取る自作 processor の最小例                                                                |
| [`katachi-baseline.json`](katachi-baseline.json) | baseline の台帳。`service/LegacyHealthCheck.kt` と `service/LegacyStatusService.kt` の2件を意図的に残して棚上げしている（[`../README.md`](../README.md#baseline)） |

## 実行方法

Android SDK は要りません。JDK 17 だけで動きます。

```sh
cd sample/jvm

# ファイルの配置と konsist { } の制約を検査する
./gradlew :architecture-test:test

# 定義からドキュメントを docs/ に生成する
./gradlew :architecture-test:katachiDocs

# 自作の processor を実行する
./gradlew :architecture-test:katachiRoleNames --arg prefix=domain

# Service のテンプレートから src/main/kotlin/com/example/service/GreetingService.kt を生成する
./gradlew :architecture-test:katachiTemplate --arg roleName=Service --arg name=Greeting
```

リポジトリのルートからは、CI と同じ一式（テンプレートから生成 → 検査 → 生成物を削除、まで含む）を1コマンドで回せます。

```sh
./gradlew checkSampleJvm
```
