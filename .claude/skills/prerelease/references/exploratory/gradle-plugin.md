# チャーター: Gradle plugin

- 利用者像: Gradle の組み合わせが違う人（古い Gradle・KMP・マルチモジュール・configuration cache）
- 場所: クラウド（AGP を使うものは Mac）
- 目安: 60 分

## 観点

- 初めて: `scaffold` が書いた設定のまま apply して、タスク一覧に `katachi…` が出るか
- 既定のまま: 設定を足さずに apply したときの既定の動き（363 型）
- 環境違い: Gradle 8.0 と最新、JDK の版、Kotlin の版、Groovy の build.gradle、version catalog の有無
- わざと間違える: プラグインの設定の打ち間違い・存在しないモジュール・循環

## 入口

- 使ってよい: mavenLocal に publish したプラグイン・9-1 の fixture（`install-kit-fixtures.sh` の 16 種）を土台にしてよい（ただし**そこに無い形**を作るのが目的）
- 使わない: リポジトリ自身の `includeBuild`

## やってみること

- fixture にない組み合わせを 3 つ作る（例: Gradle 8.0 + Groovy + 手書きの settings、`buildSrc` あり、composite build）
- configuration cache を有効にして 2 回走らせ、2 回目が正しく再実行・キャッシュされるか
- 失敗したときのメッセージが、直し方（どの設定を足すか）を示すか
- `./gradlew tasks --all` で katachi のタスクの名前・説明が読めるか（日本語・英語）
- AGP を使うもの（Android）は Mac の回に回す。クラウドでは触れなかったと「触れなかったところ」に書く

## 過去に見つかったもの

- なし（初回）
