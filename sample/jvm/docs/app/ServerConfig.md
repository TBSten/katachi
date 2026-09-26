[Ktor サンプルアプリ](../README.md) / [アプリケーション](README.md)

# サーバ設定

実行時に読み込まれる設定ファイル。Kotlin ではない資源も役割を持つ

起動したプロセスが読む設定です。`src/main/resources` に置かれ、ビルド時ではなく実行時に
効きます。役割を持つのは Kotlin のファイルだけではない、という例でもあります。

`application.conf` は待ち受けポート（`18080`、環境変数 `PORT` で上書き）と、起動時に適用する
モジュール `com.example.ApplicationKt.module` を書きます。エントリポイントの
`Application.module()` と名指しで対になっていて、片方だけ直すと起動しません。
`logback.xml` はログの出力先と書式を決めます。

`layout { }` はワイルドカードではなく2つのファイルを名前で並べています。実行時に効く設定が
3つ目に増えたら、それは黙って増えてよいものではなく、気づきたいものだからです。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/resources/application.conf` |  |
| `:` | `src/main/resources/logback.xml` |  |

## Examples

- `application.conf` ... 待ち受けポートと適用モジュール
- `logback.xml` ... ログの出力先と書式

## 置いてはいけないもの

- ビルドの設定。依存やプラグインは Gradle スクリプトの役割です
- 開発者ごとに違う値や秘密情報。`local.properties` は `.gitignore` に入っていて、
  既定の `files = gitTracked()` ではそもそも検査に渡りません
