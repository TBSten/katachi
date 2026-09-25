# Ktor サンプルアプリ ドキュメント

Ktor の小さな HTTP サーバを、katachi で形から説明したもの。このページ以下はすべて `--processor=docs` が生成したもので、手では書かない。

## Document map

### [API](./api/README.md)

- [コントローラ](./api/Controller.md)
- [Ktor プラグイン設定](./api/KtorPlugin.md)

### [ドメイン](./domain/README.md)

- [サービス](./domain/Service.md)
- [モデル](./domain/Model.md)

### [データ](./data/README.md)

- [リポジトリ](./data/Repository.md)

### [アプリケーション](./app/README.md)

- [エントリポイント](./app/Entrypoint.md)
- [サーバ設定](./app/ServerConfig.md)

### [テスト](./testing/README.md)

- [テストコード](./testing/Test.md)
- [アーキテクチャ定義](./testing/ArchitectureDefinition.md)
- [生成ドキュメント](./testing/GeneratedDocumentation.md)

## API

HTTP に面する層。リクエストを受け取り、応答を返すところまでを持つ

- [コントローラ](./api/Controller.md) ... HTTP のリクエストを1つ受け取り、対応する Service を呼んで結果を返す
- [Ktor プラグイン設定](./api/KtorPlugin.md) ... Ktor の Application に対する横断的な設定を1つ行う

## ドメイン

アプリ固有の振る舞いと、その対象になる値

- [サービス](./domain/Service.md) ... アプリ固有の振る舞いを1つ持ち、Repository を組み合わせて実現する
- [モデル](./domain/Model.md) ... ドメインで扱う値。API の入出力としてもそのまま使う

## データ

値がどこから来るのかを引き受ける層

- [リポジトリ](./data/Repository.md) ... データの取得・保存を担い、取得元の詳細をドメインから隠す

## アプリケーション

プロセスの起動と、実行時に読み込まれる設定

- [エントリポイント](./app/Entrypoint.md) ... プロセスの起動と、Ktor の Application モジュールの組み立て
- [サーバ設定](./app/ServerConfig.md) ... 実行時に読み込まれる設定ファイル。Kotlin ではない資源も役割を持つ

## テスト

振る舞いを確かめるテストと、この定義そのもの、そこから生成されるドキュメント

- [テストコード](./testing/Test.md) ... src/test/kotlin に置かれるテスト。本体と同じ package 構成を保つ
- [アーキテクチャ定義](./testing/ArchitectureDefinition.md) ... katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない
- [生成ドキュメント](./testing/GeneratedDocumentation.md) ... この定義から書き出され、リポジトリにコミットされる Markdown
