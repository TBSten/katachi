[katachi-sample-android](../README.md) / [各画面の構成](README.md)

# 画面の部品

1つの画面でしか使わない @Composable。:feature:<name> の component package に <Name>*.kt で置く

Screen が大きくなってきたときに切り出す、その画面専用の部品。`:feature:home` なら
`component/HomeUserCard.kt` のように、feature モジュールの `component` package に置き、
ファイル名はモジュール名（`Home`）で始める。1つの feature に何個あってもよい。

共通コンポーネント役割との違いは、使う画面の数。2つ目の feature から呼びたくなったら
`:ui` の component package へ移して `App*` にする。feature 同士は互いに依存しないので、
ここに置いたままでは他の feature からは呼べない。

テンプレートから生成できる。`:feature:*` の `*` に `feature` と名前を付けてあるので、
`--arg feature=home --arg name=UserCard` で `HomeUserCard.kt` が `:feature:home` に入る。
`feature` に渡せるのは実在する feature モジュールの名前だけ。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:<feature>` | `src/main/kotlin/**/component/<feature>*.kt` |  |

## Examples

- `HomeUserCard` ... ホーム画面だけで使うカード（例）

## 置いてよいもの

- 値とコールバックを受け取る `internal` な `@Composable`
- その部品の `@Preview`（`PreviewRoot { }` で包む。プレビュー役割を参照）

## 置いてはいけないもの

- ViewModel への依存。状態は Screen から値で受け取る
- 他の feature の型、`public` な宣言。外から見えるのは Route だけ
