[katachi-sample-android](../README.md) / [UI (共通レイヤー)](README.md)

# テーマ

:ui モジュールの theme package に置く、色・タイポグラフィ・形

アプリの見た目の土台を決める1箇所。`AppTheme` が `MaterialTheme` を
`lightColorScheme()` / `darkColorScheme()` で包み、`darkTheme` の既定値は
`isSystemInDarkTheme()` なので、呼び出し側は何も書かなければ端末の設定に従う。

`layout` はワイルドカードではなく `AppTheme.kt` と名指ししてある。テーマは1つしか無く、
2つ目のファイルがここに現れたら「もう1つのテーマ」が静かに増えたということなので、
検査で落とす。色を足すときも、`AppTheme.kt` の中の `LightColorScheme` /
`DarkColorScheme` を書き換える。

アプリの入口（`MainActivity`）とプレビューの土台（`PreviewRoot`）がここを包むので、
共通コンポーネントも feature の Screen も、自分がどちらのテーマにいるかを知らずに
`MaterialTheme.colorScheme` / `MaterialTheme.typography` から読める。

置いてはいけないもの:

- 1つの画面・1つの部品でしか使わない色や寸法。使う場所に書く
- 背景や `Surface` の指定。プレビューの背景は `PreviewRoot`、実画面の背景は
  各 Screen が決める

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:ui` | `src/main/kotlin/**/theme/AppTheme.kt` |  |

## 例

- `AppTheme` ... アプリのテーマ
