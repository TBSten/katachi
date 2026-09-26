[katachi-sample-android](../README.md) / [各画面の構成](README.md)

# Screen

1つの画面の UI 実装となる @Composable。:feature:<name> ごとに <Name>Screen.kt を置く

画面そのものを描く `@Composable`。`:feature:home` なら `HomeScreen.kt` が1つ、という
対応が固定で、ファイル名はモジュール名から決まる。`:feature:home` に `ProfileScreen.kt` を
置くことはできず、`HomeScreen.kt` を消すこともできない。画面が2つになったら
feature モジュールごと分ける。

1つのファイルに `HomeScreen` を2つ重ねて置く。どちらも `internal`。ナビゲーションから
呼ばれる方は `viewModel()` を既定引数で受け取り、`collectAsStateWithLifecycle()` で
状態を集めてもう一方へ渡すだけ。もう一方は `UiState<HomeContent>` とコールバックだけを
受け取る状態の関数で、`@Preview` が触るのはこちら。

ここに置いてよいもの:

- 画面のレイアウトと、`UiState` の `Loading` / `Content` / `Error` の出し分け
- `:ui` の共通コンポーネント（`AppButton` など）と Material3 の呼び出し
- この画面のための `@Preview`（プレビュー役割を参照）

置いてはいけないもの:

- 状態の組み立てと保持。ViewModel の仕事で、`HomeContent` のような状態の型も
  ViewModel と同じファイルに置く
- `NavHostController` への依存。画面から出ていく遷移は、引数で受け取った
  コールバック（`onNavigateToSettings` / `onNavigateUp`）を呼ぶだけにする
- 他の feature の型。feature 同士は互いを参照せず、`:app` が Route 越しにつなぐ

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:*` | `src/main/kotlin/**/<name>Screen.kt` |  |

## Examples

- `HomeScreen` ... ホーム画面
- `SettingsScreen` ... 設定画面
