[Ktor サンプルアプリ](../README.md) / [ドメイン](README.md)

# サービス

アプリ固有の振る舞いを1つ持ち、Repository を組み合わせて実現する

このアプリが何をするのかを書く場所です。Controller から呼ばれ、必要な Repository を
組み合わせて、モデルを返します。`HealthService.currentHealth()` はいまのところ
`HealthRepository.load()` の結果を返すだけですが、判断が増えたときに増える先はここです。

ファイル名は `*Service.kt` で、1ファイル1クラス。アプリの役割ではこの役割だけが `konsist { }` で
「public であること」を制約として書いており、うっかり `internal` を付けるとテストが
落ちます。別パッケージの Controller から参照できなくなる前に気づけます。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/kotlin/**/service/*Service.kt` |  |

## Constraints

- public であること

## Examples

- `HealthService` ... サーバ稼働状態の取得

## 置いてよいもの

置いてよいのは、アプリ固有の手順・判断・組み立てです。複数の Repository をまたぐ処理や、
取得した値を突き合わせる処理はここに来ます。

## 置いてはいけないもの

- `Route`・`call`・`respond` といった Ktor の型。HTTP を知るのは API 層までです
- 取得元の詳細（接続先・クエリ・ファイルパス）。それはリポジトリが隠します
- 値の定義そのもの。data class はモデルの役割です
