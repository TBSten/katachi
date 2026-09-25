[Ktor サンプルアプリ](../README.md) / [アプリケーション](README.md)

# エントリポイント

プロセスの起動と、Ktor の Application モジュールの組み立て

プロセスが始まる1点です。`Application.kt` に `main()` と `Application.module()` の2つだけを
置きます。`main()` は `EngineMain.main(args)` を呼び、エンジンが
`src/main/resources/application.conf` を読んで待ち受けを始めます。

`Application.module()` は `configureSerialization()` と `configureRouting()` を並べるだけの
関数です。ここを読めば、このアプリにどんな横断的設定がどの順で入っているかが分かります。
プラグイン設定を1つ足すときに触るのも、この並びの1行です。

置いてはいけないもの:

- `install(...)` の中身。設定そのものは Ktor プラグイン設定の役割で、ここには呼び出しだけ
- エンドポイントの登録。`routing { }` は `plugin/Routing.kt` が持ちます
- 待ち受けポートや適用モジュールの一覧。それは `application.conf`（サーバ設定）側です

この役割の `layout { }` にはワイルドカードが無く、`Application.kt` が1つあることを
要求します。消せば `[MissingFile]` が出るので、起動点がどこにも無い状態で
通り過ぎることはありません。

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:` | `src/main/kotlin/**/Application.kt` |  |

## 例

- `Application.kt` ... プロセスの起動点
