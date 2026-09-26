[katachi-sample-kmp](../README.md)

# UI

:ui と :navigation。どの画面にも属さない共有の UI と、遷移先の定義

画面から使われる側の、共有モジュール2つです。特定の画面に属さないものがここに集まります。

`:ui` は1つのモジュールを package で割っています。`component`（部品）、`theme`（見た目）、
`core`（画面の状態の型）、`preview`（プレビューの土台）の4つで、それぞれが役割1つです。
以前は `:ui:component` のような別モジュールでしたが、統合して package にしました。
「この役割はこのモジュールのこの package」と言えることが katachi に要る形だからで、
実際の現場でもこちらの方がよく見ます。

`:navigation` は同じ group にいますが Compose に依存しません。遷移先の一覧と現在地だけを
持ち、それをどう見せるかは `:app:android` の `AppRoot` の仕事です。画面から使われる側で
あることは `:ui` と同じなので、この group に置いています。

layout はどれもモジュールパスから始まり、その下の package は `modulePackage` から
導きます。ディレクトリ名を書き写すのではなく、ビルドが言っていることをそのまま書く、
というのがこのサンプル全体の方針です。

| Role | Summary |
|---|---|
| [共通コンポーネント](./Component.md) | :ui モジュールの component package。複数の画面から使われる @Composable 部品 |
| [テーマ](./Theme.md) | :ui モジュールの theme package。MaterialTheme の設定と、色・余白のデザイントークン |
| [UI 基盤](./UiCore.md) | :ui モジュールの core package。画面に依存しない UI の土台。UiState など |
| [プレビュー](./Preview.md) | @Preview を付けた private @Composable。対象の Composable と同じ package の <対象>Preview.kt に置き、中身は PreviewRoot で包む |
| [プレビューの土台](./PreviewRoot.md) | :ui モジュールの preview package。@Preview の中身を AppTheme と Surface で包む |
| [ナビゲーション](./Navigation.md) | 遷移先の定義と、現在地を持つ Navigator |

## Placement in this group

```
:ui
  src/commonMain/kotlin/**/
    component/
      *.kt                                    共通コンポーネント
      *Preview.kt                             プレビュー
    theme/*.kt                                テーマ
    core/*.kt                                 UI 基盤
    preview/PreviewRoot.kt                    プレビューの土台

:feature:*
  src/commonMain/kotlin/**/<name>*Preview.kt  プレビュー

:navigation
  src/commonMain/kotlin/**/*.kt               ナビゲーション
```

## 置いてはいけないもの

- 画面ごとの Screen / ViewModel / Route。それは feature group です
- feature モジュールへの依存。依存は常に feature から `:ui` / `:navigation` へ向きます。
  逆向きの参照が1つ入ると、画面を足すたびに共有モジュールが太ります
