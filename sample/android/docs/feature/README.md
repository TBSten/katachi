[katachi-sample-android](../README.md)

# 各画面の構成

画面1つぶんのモジュール。:feature:<name> ごとに Screen / ViewModel / Route を1つずつ置く

`:feature:home` `:feature:settings` のような、画面1つぶんのモジュールの中身。
どのモジュールにも `<Name>Screen.kt` `<Name>ViewModel.kt` `<Name>Route.kt` が
1つずつあり、ファイル名はモジュール名から決まる。画面が増えるときは
モジュールごと増やすので、1つの feature に2つ目の画面は入らない。

3つに割ってあるのは、変わる理由が別々だから。Screen は見た目、ViewModel は状態、
Route は外とのつなぎ目で、この中で feature の外から参照されるのは Route だけ。
`:app` が知っているのも Route だけで、Screen と ViewModel はモジュールの中に閉じる。

共通レイヤー（`:ui` と `:navigation`）を同じ group に入れていないのは、増え方が違うから。
feature は「足すのが当たり前」の場所なので `":feature:*"` と書いてあり、
`settings.gradle.kts` に `include(":feature:profile")` を足せば、この定義を1行も
触らずに検査対象になる。共通レイヤーに1つ足すのは毎回が設計判断で、そちらは
UI (共通レイヤー) group にある。

feature 同士は互いに依存しない。別の画面へ遷移するときも、遷移先の決定は `:app` 側にあり、
feature が受け取るのはコールバック1つ。つながりは `:app` の1箇所にしかないので、
feature を消すときに他の feature を読み直さなくて済む。

| Role | Summary |
|---|---|
| [Screen](./Screen.md) | 1つの画面の UI 実装となる @Composable。:feature:<name> ごとに <Name>Screen.kt を置く |
| [ViewModel](./ViewModel.md) | 画面の状態を StateFlow で公開し、イベントを受け取る androidx.lifecycle.ViewModel |
| [Route](./Route.md) | 画面への遷移先。feature の外に公開する唯一の入口 |

## Placement in this group

```
:feature:*
  src/main/kotlin/**/
    <name>Screen.kt     Screen
    <name>ViewModel.kt  ViewModel
    <name>Route.kt      Route
```
