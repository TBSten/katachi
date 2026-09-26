[katachi-sample-kmp](../README.md) / [UI](README.md)

# テーマ

:ui モジュールの theme package。MaterialTheme の設定と、色・余白のデザイントークン

アプリの見た目の素を1か所にまとめた package です。`AppTheme` がアプリ唯一のテーマで、
エントリポイント（`AppRoot`）と、すべてのプレビューが通る `PreviewRoot` がこれで包みます。
テーマが2つに割れないよう、ここ以外で `MaterialTheme { }` を直接書きません。

`AppSpacing` が `AppTheme` の隣にいるのは、Material 3 が余白のスキームを持たないからです。
色は `ColorScheme` 経由で配れますが、余白は配る仕組みが無いので、部品が直接読む
`object` として置いています。トークンを足すならこの package です。

Android の `res/values/themes.xml` ではなく Kotlin 側にテーマを持っているのは KMP だからです。
iOS には `res/` がないので、リソース XML に書いた見た目は共有できません。アプリ名のような
Android ビルドが要求するものだけが `:app:android` の AndroidResource に残ります。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:ui` | `src/commonMain/kotlin/**/theme/*.kt` |  |

## Examples

- `AppTheme` ... アプリ全体のテーマ
- `AppSpacing` ... 余白のトークン

## 置いてはいけないもの

- 1つの画面だけで使う色や寸法。それはその画面の中の定数です
- `@Composable` の部品。`component` package に置きます
