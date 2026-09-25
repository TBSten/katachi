[katachi-sample-android](../README.md)

# UI (共通レイヤー)

feature をまたいで共有する UI。:ui の4つの package と :navigation

どの画面からも使われる UI と、その土台。`:ui` は1つのモジュールを package で割ってあり、
`component`（共通部品）・`theme`（色とタイポグラフィ）・`core`（UI 層の語彙）・
`preview`（プレビューの土台）の4つ。`:navigation` は画面遷移の窓口だけを持つ別モジュール。

ここに置いてよいのは、2つ以上の feature が使うもの、あるいは使うと決まっているもの。
1つの画面でしか使わないものは、その feature モジュールに置く。`:ui` は下の層も
横の feature も知らないので、`:data` や `:feature:*` への依存は入らない。

feature 側の Screen / ViewModel / Route がここに無いのは、増え方が違うから。
あちらはモジュールを足せば勝手に増える場所で、こちらは1つ足すたびに
「全画面で使うのか」を決める場所なので、group を分けてある。

`:navigation` が `:ui` と別モジュールなのは、依存の向きのため。feature は
`AppNavigator` インターフェースにだけ依存し、`NavHostController` には触らない。
画面部品を使いたいだけのコードにナビゲーションの依存を持ち込ませないためでもある。
グラフの組み立ては `:app` が行う。

| 役割 | 概要 |
|---|---|
| [共通コンポーネント](./Component.md) | :ui モジュールの component package に置く、feature をまたいで使う部品 |
| [テーマ](./Theme.md) | :ui モジュールの theme package に置く、色・タイポグラフィ・形 |
| [UI 基盤](./UiCore.md) | :ui モジュールの core package に置く、UI 層の土台になる型 |
| [プレビュー](./Preview.md) | @Preview を付けた private @Composable。対象の Composable と同じファイルに置き、中身は PreviewRoot で包む |
| [プレビューの土台](./PreviewRoot.md) | :ui モジュールの preview package に置く、すべての @Preview が中身を包む土台。テーマと背景を 1 箇所で決め、darkTheme を受け取って明暗を出し分ける |
| [画面遷移](./Navigation.md) | :navigation に置く、画面間の移動 |

## このグループの配置

```
:ui
  src/main/kotlin/**/
    component/*.kt          共通コンポーネント
    theme/AppTheme.kt       テーマ
    core/*.kt               UI 基盤
    preview/PreviewRoot.kt  プレビューの土台

:navigation
  src/main/kotlin/**/*.kt   画面遷移
```
