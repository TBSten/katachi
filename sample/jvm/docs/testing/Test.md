[Ktor サンプルアプリ](../README.md) / [テスト](README.md)

# テストコード

src/test/kotlin に置かれるテスト。本体と同じ package 構成を保つ

アプリケーションの振る舞いを確かめるコードです。対象と同じ package に置き、
`src/test/kotlin` の下が `src/main/kotlin` の鏡像になるようにします。

`HealthRouteTest` は kotest の `FreeSpec` で書かれ、Ktor の `testApplication` を立てて
`GET /health` に実際にリクエストを投げます。テスト名は日本語の1文で、何を確かめるのかを
そのまま書きます。レイアウトのチェックが通るだけで中身が死んでいる、という状態に
しないための押さえです。

ここに入らないもの:

- アーキテクチャ定義そのもののテスト。それらは `:architecture-test` にあり、
  アーキテクチャ定義の役割が覆います。この役割が見ているのはルートプロジェクト（`:`）の
  テストソースセットだけです

`layout { }` はファイル名を縛っていません（`**` がパッケージの階層、その下の `*` が
任意の `.kt` 1ファイル）。代わりに、`.kt` を1つも持たないディレクトリがテストソースセットの
下に残っていれば報告されます。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/test/kotlin/**/*.kt` |  |

## Examples

- `HealthRouteTest` ... GET /health のテスト
