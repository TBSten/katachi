[katachi-sample-kmp](../README.md) / [データ](README.md)

# リポジトリ

:data モジュールの user package。データの取得口で、インターフェースと実装の2つの置き方を持つ

アプリがデータに触る入口です。`:data` の `user` package に、インターフェース
（`*Repository.kt`）と実装（`*RepositoryImpl.kt`）を並べて置きます。呼ぶ側
（ViewModel）が依存するのはインターフェースの方だけです。

layout のパターンを2行に分けてあるのは、`*Repository.kt` が
`UserRepositoryImpl.kt` に一致しないからです。`*` は後ろに続く文字列を越えないので、
実装を拾うには実装のパターンを書く必要があります。1行で済ませようとして
`*Repository*.kt` と緩めると、`UserRepositoryFactory.kt` のような宣言していない形まで
通ってしまいます。

`commonMain` だけを宣言しています。プラットフォームで実装が変わるものは、この役割では
なく隣の PlatformImplementation（expect/actual）が引き受けます。ここに `androidMain` を
足すと、同じ「プラットフォーム差の吸収」が2か所に散ります。

置いてはいけないもの:

- UI の型。`UiState` は `:ui` の core package にあり、`:data` はそれを知りません
- テスト用の偽実装。`FakeUserRepository` は `:testing` の Fake の役割です

sample/android と違い、このサンプルには設定用のリポジトリがありません。設定画面は
`UserRepository` と `platformName()` を読むだけで足りています。使われていない package を
定義に書くと、実体の無いディレクトリをドキュメントが案内することになります。

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:data` | `src/commonMain/kotlin/**/user/*Repository.kt` |  |
| `:data` | `src/commonMain/kotlin/**/user/*RepositoryImpl.kt` |  |

## 例

- `UserRepository` ... ユーザーを取得するインターフェース
- `UserRepositoryImpl` ... UserRepository の実装
