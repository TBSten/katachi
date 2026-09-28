[katachi-sample-kmp](../README.md)

# フィーチャー

1画面につき1モジュール。:feature:<name> が Screen / ViewModel / Route を必ず持つ

画面ごとに1つずつ増えていくモジュールの層です。`:feature:home` と `:feature:settings` が
あり、画面を足すときはモジュールを足します。

Screen / ViewModel / Route の3つをここに集めたのは、どれも「1つの画面のためのもの」
だからです。3つとも `wildcard("feature")`、つまり `:feature:*` が捕まえたモジュール名を
読み戻してファイル名を決めるので、`:feature:home` には `HomeScreen.kt` /
`HomeViewModel.kt` / `HomeRoute.kt` が要ります。「画面が1つあればいい」ではなく
「その名前の画面が要る」まで言い切れるのがこの group の形です。

ui group と分けてあるのは、増え方が違うからです。feature はモジュールが増える前提の
場所で、`":feature:${capture("feature")}".module { }` という1つの宣言が何個あっても
足ります。一方 `:ui` や `:navigation` に何かを足すのは設計判断です。1つの group にまとめると、
生成されるドキュメントでその差が消えてしまいます。

画面の部品（FeatureComponent）だけは1つの feature の中で数が増えていく役割で、
テンプレートから生成できます。`:feature:*` の `*` に `feature` と名前を付けてあるので、
`--arg feature=home` で生成先のモジュールを選びます。

この group の役割はすべて `commonMain` です。画面まわりに `androidMain` / `iosMain` は1つも
ありません。プラットフォーム差は data group の PlatformImplementation に閉じています。

| Role | Summary |
|---|---|
| [画面](./Screen.md) | 1つの画面の @Composable。ViewModel の StateFlow を購読し、Component を組み合わせて描く |
| [ViewModel](./ViewModel.md) | 画面の状態を持つ androidx.lifecycle.ViewModel。Repository から取得した値を UiState に変換し、StateFlow で公開する |
| [ルート](./Route.md) | 画面を navigation の Destination に結びつけ、ViewModel の生成も引き受ける |
| [画面の部品](./FeatureComponent.md) | 1つの画面でしか使わない @Composable。:feature:<name> の commonMain の component package に <Name>*.kt で置く |

## Placement in this group

```
:feature:<feature>
  src/commonMain/kotlin/**/
    <feature>Screen.kt       画面
    <feature>ViewModel.kt    ViewModel
    <feature>Route.kt        ルート
    component/<feature>*.kt  画面の部品
```

## 置いてはいけないもの

- 複数の画面から使う部品。それは `:ui` の Component です
- データの取得。`:data` にあり、feature はインターフェース越しに読みます
- 他の feature への依存。画面どうしは直接つながらず、`:navigation` の `Destination`
  を介します
