[katachi-sample-kmp](../README.md) / [UI](README.md)

# UI 基盤

:ui モジュールの core package。画面に依存しない UI の土台。UiState など

UI 層の語彙のうち、見た目でも部品でもないものを置く package です。今あるのは `UiState`
だけで、「読み込み中 / 読み込み済み / 失敗」という、どの画面にも共通する3状態を表す
sealed interface です。

`UiState` はわざと Compose に依存させていません。ViewModel が作って `@Composable` が
読むという両端の型なので、素の Kotlin にしておけば Compose ランタイム無しで両側を
テストできます。実際 `:app:android` の `SampleModulesSpec` は Compose を起動せずに
`valueOrNull()` の挙動を確かめています。

`core` という名前は「基盤」以上のことを言っていないので、なんでも入る置き場になりがちです。
「画面を知らない」「Compose を知らない」の2つを満たすかどうかで判断してください。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:ui` | `src/commonMain/kotlin/**/core/*.kt` |  |

## Examples

- `UiState` ... 画面の状態を表す型
- `valueOrNull` ... 値を取り出す拡張関数

## 置いてよいもの

- 画面の状態を表す型と、その小さな拡張関数
- 複数の画面が共通で使う、UI 側だけの語彙

## 置いてはいけないもの

- `@Composable`。部品は `component`、見た目は `theme` です
- ドメインの型。ユーザーやその一覧は `:data` の持ち物で、`UiState` はそれを包むだけです
- 特定の画面でしか意味を持たない状態。それは feature モジュール側に書きます
