[katachi-sample-kmp](../README.md) / [UI](README.md)

# プレビューの土台

:ui モジュールの preview package。@Preview の中身を AppTheme と Surface で包む

プレビューを包む wrapper だけの package です。`PreviewRoot` は中身を `AppTheme` と
`Surface` で包みます。`AppTheme` が本番と同じ配色を与え、`Surface` がその背景色を
後ろに塗ります。これが無いと、すべてのプレビューが同じ2行を書き写すことになり、
テーマに引数が増えた日に一斉に食い違います。

名前が似ている ui/Preview とは別の役割です。こちらは包む側、あちらは包まれる側で、
ここに `@Preview` を1つも書きません。逆に、プレビュー以外から `PreviewRoot` を
呼ぶこともありません。

`commonTest` ではなく `commonMain` に置いてあるのは、使う側の `@Preview` が
feature モジュールの `commonMain` にいるからです。テスト source set は他モジュールから
参照できないので、そこに置くと届きません。

この役割はワイルドカードを使わずファイル名を書ききっている数少ない宣言なので、
`PreviewRoot.kt` が消えたり改名されたりすると `[MissingFile]` で報告されます。

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:ui` | `src/commonMain/kotlin/**/preview/PreviewRoot.kt` |  |

## 例

- `PreviewRoot` ... すべての @Preview が使う wrapper
