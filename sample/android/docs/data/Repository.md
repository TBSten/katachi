[katachi-sample-android](../README.md) / [データレイヤー](README.md)

# リポジトリ

データの取得と保存。インターフェースと実装を :data の、扱う対象ごとの package （user / settings）に並べて置く

データの取得と保存を引き受ける、`:data` の唯一の役割。扱う対象ごとに package を割り
（`user` / `settings`）、その中にインターフェース（`*Repository.kt`）と実装
（`*RepositoryImpl.kt`）を並べる。呼び出し側が依存するのはインターフェースだけで、
`Impl` の名前を ViewModel の引数に書くことはない。

この役割は `layout { }` を2つ持つ。置き場所は同じ package で、違うのはファイル名の
パターンだけなので、どちらがインターフェースでどちらが実装かは `layout` の
`description` が説明する。1つの役割が複数の置き場所を持てることの実例でもある。

`:data` は他のモジュールに依存しない、このアプリで一番下の層。Android にも Compose にも
触らないので、インターフェースは素の Kotlin として読める。ViewModel はコンストラクタで
インターフェースを受け取り、テストでは `:testing` の `Fake*` に差し替える。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:data` | `src/main/kotlin/**/user/*Repository.kt` | インターフェース。呼び出し側が依存する型 |
| `:data` | `src/main/kotlin/**/settings/*Repository.kt` | インターフェース。呼び出し側が依存する型 |
| `:data` | `src/main/kotlin/**/user/*RepositoryImpl.kt` | インターフェース。呼び出し側が依存する型 |
| `:data` | `src/main/kotlin/**/settings/*RepositoryImpl.kt` | インターフェース。呼び出し側が依存する型 |

## Examples

- `UserRepository` ... ユーザーの取得と保存のインターフェース
- `UserRepositoryImpl` ... UserRepository の実装

## 置いてはいけないもの

- 画面向けの型。`UiState` に詰め替えるのは ViewModel の仕事で、`:data` は `:ui` を知らない
- `*Repository.kt` `*RepositoryImpl.kt` 以外のファイル。DTO やデータソースを
  分けたくなったら、まず役割を増やす
- 対象をまたぐ package。`user` の型が `settings` に混ざったら package を割り直す
