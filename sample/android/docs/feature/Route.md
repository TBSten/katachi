[katachi-sample-android](../README.md) / [各画面の構成](README.md)

# Route

画面への遷移先。feature の外に公開する唯一の入口

feature モジュールが外に見せる唯一のもの。`HomeRoute` のような object が遷移先の
パスを `PATH` として持ち、`NavGraphBuilder.homeScreen(...)` のような拡張関数が
自分の画面をグラフに1つ登録する。ファイル名は `<Name>Route.kt` でモジュール名から決まる。

グラフを組み立てるのは `:app` の `AppNavHost` だけで、`:app` が触るのは
`HomeRoute.PATH` と `homeScreen(...)` のような、この役割が公開するものに限られる。
`HomeScreen` も `HomeViewModel` も `:app` からは呼ばれない。feature が増えても
`:app` に増えるのは1行で済む。

画面から出ていく遷移は、この拡張関数の引数で受け取ったコールバック
（`onNavigateToSettings` / `onNavigateUp`）を Screen に渡す形で書く。
どの画面へ行くかを決めているのは `:app` 側で、feature が別の feature の Route を
import することはない。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:*` | `src/main/kotlin/**/<name>Route.kt` |  |

## Examples

- `HomeRoute` ... ホーム画面への遷移先
- `SettingsRoute` ... 設定画面への遷移先

## 置いてはいけないもの

- UI。`@Composable` として描くのは Screen で、ここは `composable(...)` への登録だけ
- 他の feature の Route への参照
- 引数の組み立て以上のロジック。遷移の判断は呼び出し元にある
