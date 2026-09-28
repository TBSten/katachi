[katachi-sample-android](../README.md) / [UI (共通レイヤー)](README.md)

# 共通コンポーネント

:ui モジュールの component package に置く、feature をまたいで使う部品

複数の画面から呼ばれる Compose の部品。`AppButton` のように Material3 の上へ薄く被せて、
形や強弱を変えたときに全画面へ一度に届くようにするために置く。

見た目の選び分けは、この package の中の型で表す。`AppButton` は
`emphasis: AppButtonEmphasis` を取り、`Filled` と `Outlined` の出し分けを内側でやる。
呼び出し側が Material3 の `Button` と `OutlinedButton` を直接使い分けなくて済むようにするのが
この役割の狙いなので、部品と、その部品のための enum や `@Preview` は同じファイルにまとめる。

ファイル名は `*.kt` で縛っていない。部品は増えることが前提だから。

テンプレートから生成できる。ファイル名まるごとが `capture("name")` なので、
`--arg template=Component --arg name=AppLabel` で `AppLabel.kt` ができる
（`App` から始めるのはこの役割の慣習であって、layout が強制してはいない）。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:ui` | `src/main/kotlin/**/component/*.kt` |  |

## Examples

- `AppButton` ... アプリ共通のボタン

## 置いてよいもの

ここに置いてよいのは、2つ以上の feature が使うもの、あるいは使うと決まっているもの。
1つの画面でしか使わない部品は、その feature モジュールに置く。

## 置いてはいけないもの

- 状態の保持。値とコールバック（`text`、`onClick`）で受け渡し、`remember` で
  抱え込まない
- `:data` や `:feature:*` への依存。`:ui` は下の層も横の feature も知らない
- 色やタイポグラフィの直書き。`MaterialTheme` から読む（テーマ役割を参照）
