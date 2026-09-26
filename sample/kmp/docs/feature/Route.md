[katachi-sample-kmp](../README.md) / [フィーチャー](README.md)

# ルート

画面を navigation の Destination に結びつけ、ViewModel の生成も引き受ける

feature モジュールの外から触れる唯一の入口です。`object HomeRoute` が
`destination`（この画面がどの `Destination` なのか）と `Content()`（描画の呼び出し口）を
持ち、呼ぶ側はその2つしか知りません。

ViewModel の生成をここが引き受けているのが要点です。`Content()` の中で
`viewModel { HomeViewModel(repository) }` を呼ぶので、`:app:android` の AppRoot は
HomeViewModel という型の存在を知らずに画面を出せます。依存（今は UserRepository）は
引数で受け取ります。このサンプルは DI コンテナを持たず、手渡しで済ませています。

置いてはいけないもの:

- 画面の中身。Compose のツリーを組むのは Screen です
- 遷移先そのものの定義。`Destination` は `:navigation` にあり、Route はそれを指すだけです
- 遷移の制御。今どこにいるかを持つのは `:navigation` の `Navigator` です

`commonMain` 固定にしてあるのは、Android の `AppRoot` からも、将来 `app/ios` が
`ComposeUIViewController` を持ったときにも、同じ Route を呼べるようにするためです。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:*` | `src/commonMain/kotlin/**/<name>Route.kt` |  |

## Examples

- `HomeRoute` ... ホーム画面の遷移先
- `SettingsRoute` ... 設定画面の遷移先
