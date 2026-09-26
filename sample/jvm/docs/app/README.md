[Ktor サンプルアプリ](../README.md)

# アプリケーション

プロセスの起動と、実行時に読み込まれる設定

「このプロセスはどう始まるのか」に答えるものを集めた層です。レイヤーというより、
他のどの層にも属さない起動まわりの置き場所です。

入っているのはエントリポイント（`Application.kt`）とサーバ設定（`application.conf` と
`logback.xml`）です。この2つは対になっていて、`application.conf` の `modules` が
`com.example.ApplicationKt.module` を名指しし、その関数が各プラグイン設定を呼びます。
片方だけ直すと起動しないので、同じ場所で読めるようにしてあります。

ここに置かないのは、ビルド時にしか効かない設定（Gradle スクリプトの役割）と、
個々の `install(...)` の中身（API 層の Ktor プラグイン設定の役割）です。

| Role | Summary |
|---|---|
| [エントリポイント](./Entrypoint.md) | プロセスの起動と、Ktor の Application モジュールの組み立て |
| [サーバ設定](./ServerConfig.md) | 実行時に読み込まれる設定ファイル。Kotlin ではない資源も役割を持つ |

## Placement in this group

```
:
  src/main/
    kotlin/**/Application.kt  エントリポイント
    resources/
      application.conf        サーバ設定
      logback.xml             サーバ設定
```
