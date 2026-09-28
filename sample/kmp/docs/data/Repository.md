[katachi-sample-kmp](../README.md) / [データ](README.md)

# リポジトリ

:data モジュールの user package。データの取得口で、インターフェースと実装の2つの置き方を持つ

アプリがデータに触る入口です。`:data` の `user` package に、インターフェース
（`*Repository.kt`）と実装（`*RepositoryImpl.kt`）を並べて置きます。呼ぶ側
（ViewModel）が依存するのはインターフェースの方だけです。

layout の宣言を2つに分けてあるのは、インターフェースの名前が実装のファイル名に
一致しないからです。部分一致の capture も `*` と同じく後ろに続く文字列を越えないので、
実装を拾うには実装のパターンを書く必要があります。1つのパターンで済ませようとして
緩めると、宣言していない形まで通ってしまいます。

インターフェースと実装、それぞれのファイルの宣言に `.template` を1つずつ付けてあります。
`--arg template=data.Repository.repository,data.Repository.repositoryImpl --arg name=Cache`
で両方を1回に、`data.Repository.repository` だけを指定すればインターフェースだけを
作れます（実装は手で書くか、後から `data.Repository.repositoryImpl` で追加できます）。

`commonMain` だけを宣言しています。プラットフォームで実装が変わるものは、この役割では
なく隣の PlatformImplementation（expect/actual）が引き受けます。ここに `androidMain` を
足すと、同じ「プラットフォーム差の吸収」が2か所に散ります。

sample/android と違い、このサンプルには設定用のリポジトリがありません。設定画面は
`UserRepository` と `platformName()` を読むだけで足りています。使われていない package を
定義に書くと、実体の無いディレクトリをドキュメントが案内することになります。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:data` | `src/commonMain/kotlin/**/user/*Repository.kt` |  |
| `:data` | `src/commonMain/kotlin/**/user/*RepositoryImpl.kt` |  |

## Examples

- `UserRepository` ... ユーザーを取得するインターフェース
- `UserRepositoryImpl` ... UserRepository の実装

## 置いてはいけないもの

- UI の型。`UiState` は `:ui` の core package にあり、`:data` はそれを知りません
- テスト用の偽実装。`FakeUserRepository` は `:testing` の Fake の役割です
