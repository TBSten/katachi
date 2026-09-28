[katachi-sample-kmp](../README.md) / [フィーチャー](README.md)

# 画面の部品

1つの画面でしか使わない @Composable。:feature:<name> の commonMain の component package に <Name>*.kt で置く

Screen が大きくなってきたときに切り出す、その画面専用の部品です。`:feature:home` なら
`commonMain` の `component/HomeUserCard.kt` のように置き、ファイル名はモジュール名
（`Home`）で始めます。1つの feature に何個あってもかまいません。

共通コンポーネント（ui/Component）との違いは、使う画面の数です。2つ目の画面から
使いたくなったら `:ui` の component package へ移します。feature 同士は互いに依存しないので、
ここに置いたままでは他の画面からは呼べません。

テンプレートから生成できます。`:feature:*` の `*` に `feature` と名前を付けてあるので、
`--arg feature=home --arg name=UserCard` で `HomeUserCard.kt` が `:feature:home` に入ります。
`feature` に渡せるのは実在する feature モジュールの名前だけです。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:*` | `src/commonMain/kotlin/**/component/<feature>*.kt` |  |

## Examples

- `HomeUserCard` ... ホーム画面だけで使う、ユーザー1人ぶんの表示

## 置いてよいもの

- 値とコールバックを受け取る `internal` な `@Composable`
- `:ui` の Component / Theme を組み合わせる配置の記述

## 置いてはいけないもの

- ViewModel への依存。状態は Screen から値で受け取ります
- `public` な宣言。feature の外から見えるのは Route だけです
