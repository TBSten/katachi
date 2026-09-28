[katachi-sample-android](../README.md) / [各画面の構成](README.md)

# ViewModel

画面の状態を StateFlow で公開し、イベントを受け取る androidx.lifecycle.ViewModel

`:feature:<name>` ごとに `<Name>ViewModel.kt` が1つ。画面の状態を
`StateFlow<UiState<T>>` として公開し、画面から届いたイベント（`refresh()`、
`setDarkThemeEnabled(enabled)`）を受けて次の状態を流す。Screen と同じく
ファイル名はモジュール名から決まるので、`:feature:home` に `SettingsViewModel.kt` は置けない。

状態の入れ物には `:ui` の `core` package にある `UiState` を使い、その `Content` が包む
中身（`HomeContent` / `SettingsContent`）は ViewModel と同じファイルに data class で置く。
1つの画面でしか使わない型なので `:ui` には上げない。

Repository はコンストラクタ引数で受け取り、既定値に本番実装を書いてある
（`userRepository: UserRepository = UserRepositoryImpl()`）。ファクトリ無しで
`viewModel()` が組み立てられるようにするためで、DI を入れるならこの既定値が
消えるだけで形は変わらない。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:*` | `src/main/kotlin/**/<feature>ViewModel.kt` |  |

## Examples

- `HomeViewModel` ... ホーム画面の状態
- `SettingsViewModel` ... 設定画面の状態

## 置いてはいけないもの

- Compose への依存。`androidx.compose.*` を import せず、`@Composable` も書かない。
  この線が引けているから、状態の組み立てを Compose 抜きで読める
- Android の Context / View / リソース。`androidx.lifecycle.ViewModel` を継承する以外に
  Android には触らない
- 画面のレイアウト。描くのは Screen の仕事
