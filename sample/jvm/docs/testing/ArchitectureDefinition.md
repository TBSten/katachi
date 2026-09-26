[Ktor サンプルアプリ](../README.md) / [テスト](README.md)

# アーキテクチャ定義

katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない

このプロジェクトの形を書いたコードそのものです。アプリのどのレイヤーにも属さないので、
`:architecture-test` という専用モジュールに置きます。アプリはルートプロジェクト（`:`）
なので、定義を外に出してもアプリ側の main ソースセットは1つのままです。

中身は1宣言1ファイルです。`ProjectArchitecture.kt` が入口、`groups/<Name>Group.kt` が
グループ、`roles/<Name>Role.kt` が役割、`processors/` がこのサンプル自身が書いた
プロセッサです。定義どおりかを確かめる `ProjectArchitectureTest` と、katachi 側の
結合テストである `*Spec` も同じモジュールにあるので、この役割が覆います。

`layout { }` は `**` でテストソースセット全体を見ています。`groups/` と `roles/` に
分けるのは読みやすさのための約束であって、katachi の `layout { }` が強制しているわけでは
ありません（`roles/` に何も宣言しない `.kt` を置いても通ります）。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/**/*.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... 定義の入口
- `roles/ControllerRole.kt` ... 役割1つの宣言
- `ProjectArchitectureTest.kt` ... 定義を assert するテスト

## 置いてはいけないもの

- アプリのコード。`:architecture-test` に `src/main/kotlin` を作ると、
  どの役割も覆わないファイルとして落ちます
- どのレイヤーに属するかが決まっているもの。ここは「形」だけを書く場所です
