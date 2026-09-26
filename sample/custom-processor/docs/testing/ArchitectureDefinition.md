[自作プロセッサのサンプル](../README.md) / [定義とプロセッサ](README.md)

# アーキテクチャ定義

katachi の DSL で書かれた役割の定義と、それを assert するテスト

このプロジェクトの形を書いたコードそのものです。アプリのどのレイヤーにも属さないので、
`:architecture-test` という専用モジュールに置きます。

中身は1宣言1ファイルです。`ProjectArchitecture.kt` が入口、`groups/<Name>Group.kt` が
グループ、`roles/<Name>Role.kt` が役割です。全 group・全役割が `by` で使う節の定義は
`DocumentSections.kt` にまとめてあり、`ProjectArchitecture.kt` と同じく名指しで許して
あります。定義どおりかを確かめる `ProjectArchitectureTest`、3本の processor を API から
呼ぶ `CustomProcessorSpec`、katachi 自身の番兵である `LayoutSnapshotSpec` も同じ
モジュールにあり、この役割が覆います。

`layout { }` はパッケージを `"com/example"` と直に書いています。`modulePackage` を
使わないのは、このモジュールのソースがモジュール名から導かれる
`com/example/architectureTest` ではなく `com/example` に置かれているからです。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/ProjectArchitecture.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/DocumentSections.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/ProjectArchitectureTest.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/*Spec.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/groups/*.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/roles/*.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... 定義の入口
- `roles/StoreRole.kt` ... 役割1つの宣言
- `ProjectArchitectureTest.kt` ... 定義を assert するテスト

## 置いてはいけないもの

- processor 本体。`processors/` は「プロセッサ」の役割の担当で、わざと分けてあります。
  定義は形を書くもの、processor はその形を読んで何かを作るもので、読む向きが逆だからです。
  分けておくと `RoleFileCount` の出力にも2つが別の行として出ます
- アプリのコード。`:architecture-test` に `src/main/kotlin` を作ると、
  どの役割も覆わないファイルとして落ちます
