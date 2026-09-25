[katachi-sample-kmp](../README.md) / [UI](README.md)

# プレビュー

@Preview を付けた private @Composable。対象の Composable と同じ package の <対象>Preview.kt に置き、中身は PreviewRoot で包む

`@Preview` を付けた `private @Composable` です。描く対象と同じ package の
`<対象>Preview.kt` に分けて書きます。対象のファイルに同居させる書き方もありますが、
このサンプルはファイルを分ける方を採っています（同居する形は sample/android にあります）。

置き場所は2つあります。画面のプレビューはその画面を持つ feature モジュールに、
部品のプレビューはどの画面にも属さないので `:ui` に置きます。どちらも「描く対象の隣」
という同じ規則から出てくる2箇所です。

この `@Preview` は Compose Multiplatform の `compose.preview`
（`org.jetbrains.compose.ui:ui-tooling-preview`）のものです。注釈の完全修飾名は
Android 専用の `androidx.compose.ui:ui-tooling-preview` とまったく同じなので、
IDE の補完で後者を足してしまうと iOS ターゲットが解決できなくなります。
`commonMain` でプレビューが書けているのは前者を使っているからです。

置いてよいもの:

- 状態を引数で渡せる stateless な Composable のプレビュー。`HomeScreen` ではなく
  `HomeContent` を呼ぶので、ViewModel を組み立てずに描けます
- 1つの対象につき状態ごとに複数。読み込み中・読み込み済み・失敗を並べて見られます

置いてはいけないもの:

- `public` なプレビュー。他から呼ぶものではないので `private` にします
- プレビューの中で `AppTheme { }` を直接書くこと。包むのは `PreviewRoot` の仕事です
- 本物の Repository やネットワークに触る処理。値はリテラルで書きます

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:feature:*` | `src/commonMain/kotlin/**/<name>*Preview.kt` | 画面のプレビュー。その画面を持つ feature モジュールに置く |
| `:ui` | `src/commonMain/kotlin/**/component/*Preview.kt` | 部品のプレビュー。どの画面にも属さないので :ui に置く |

## 例

- `PrimaryButtonPreview` ... PrimaryButton のプレビュー
- `HomeLoadedPreview` ... 読み込み済みのホーム画面
