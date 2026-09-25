[katachi-sample-android](../README.md) / [UI (共通レイヤー)](README.md)

# プレビューの土台

:ui モジュールの preview package に置く、すべての @Preview が中身を包む土台。テーマと背景を 1 箇所で決め、darkTheme を受け取って明暗を出し分ける

`PreviewRoot` ただ1つ。`AppTheme` で包み、`Surface` を敷いてプレビューの背景を決める。
`darkTheme` を引数で受け取って `AppTheme` にそのまま渡すので、同じ Composable の
明暗を2つの `@Preview` で並べられる。

`@Preview` はアプリの中から呼ばれないので、上にテーマを与えるものが何も無い。
各プレビューが `AppTheme { }` を自分で書いても動きはするが、それだと
背景を敷くかどうか、`darkTheme` をどう渡すかがプレビューごとにずれていく。
「プレビューが何の上に載るか」を1箇所で決めるためにこの役割がある。

`layout` はワイルドカードではなく `PreviewRoot.kt` と名指ししてあり、消すと
`[MissingFile]` で検査が落ちる。すべての `@Preview` が依存する土台なので、
黙って消えないようにしてある。

置いてはいけないもの:

- 本番の画面から呼ばれるもの。実画面のテーマは `MainActivity` が `AppTheme { }` で与える
- プレビュー用のダミーデータ。渡す状態は各 `@Preview` がその場で書く

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:ui` | `src/main/kotlin/**/preview/PreviewRoot.kt` |  |

## 例

- `PreviewRoot` ... プレビュー共通の土台
