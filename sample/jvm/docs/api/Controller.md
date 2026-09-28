[Ktor サンプルアプリ](../README.md) / [API](README.md)

# コントローラ

HTTP のリクエストを1つ受け取り、対応する Service を呼んで結果を返す

HTTP とアプリケーションの中身との境目です。パスとメソッドの登録、リクエストからの値の
取り出し、Service が返した値を応答にするところまでを持ちます。

1ファイル1コントローラで、ファイル名は `*Controller.kt`。リソースごとに
`controller/health/` のような package を1つ切り、その中に置きます。ファイル名がそのまま
エンドポイントのまとまりを表すので、新しいパスを既存のファイルに足すのか新しく作るのかを
名前だけで判断できます。なお `layout { }` が見ているのは置き場所と名前までで、
「置いてはいけないもの」を機械的に弾いてはいません。

テンプレートから生成できます。リソースの package の階層に `resource` と名前を付けてあるので、
`--arg resource=user --arg name=User` で `controller/user/UserController.kt` ができます。
`resource` は package にそのまま入るので、テンプレートは英数字だけの値しか受け付けません
（`--arg resource=user-profile` のような値は弾きます）。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/kotlin/**/controller/*/*Controller.kt` |  |

## Examples

- `HealthController` ... ヘルスチェックの受け口

## 置いてよいもの

置いてよいのは Ktor の `Route` に対する登録と、受け渡しのための変換だけです。
`HealthController` は `register(route: Route)` の中で `route.get("/health") { ... }` を
書き、呼び出す `HealthService` は既定値付きのコンストラクタ引数で受け取るので、
テストから差し替えられます。

## 置いてはいけないもの

- 条件分岐や計算。「どちらを返すか」を決めた時点で、それはサービスの仕事です
- `com.example.repository` の呼び出し。Controller から直接データを取りに行きません
- `install(...)` のような Application 全体への設定と `routing { }` 自体。どの Controller を
  routing ツリーに繋ぐかは `plugin/Routing.kt` が決めます
