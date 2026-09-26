[自作プロセッサのサンプル](../README.md)

# 定義とプロセッサ

katachi の定義、それを読む自作プロセッサ3本、そして生成されたドキュメント

このサンプルの本題が置かれている場所です。アプリのレイヤーではなく、プロジェクトを
支えているコードを集めてあります。

役割を分けているのは、読む向きが違うからです。アーキテクチャ定義は形を記述し、
プロセッサはその形を読んで何かを作り、生成ドキュメントとレイアウトスナップショットは
書き出された結果です。`processors/` を定義の役割に含めてしまうと、このサンプルが
何を見せたいのかが `docs/` からも `RoleFileCount` の出力からも消えてしまいます。

プロセッサの3本は、引数なし・型付き引数・検査（`Result.failure` で落とす）という
3つの形を1本ずつ受け持ちます。どれも `object` で、katachi 側に継承すべき基底クラスは
ありません。

生成物の2つも役割が別です。`docs/` は `katachiDocs` が読む人のために書くもの、
`snapshots/` は `LayoutSnapshotSpec` が katachi 自身のために書くもので、
更新の仕方も消したときに困る相手も違います。

ここに置いてはいけないのは、アプリの本体コードです。`:architecture-test` は
main ソースセットを持ちません。

| 役割 | 概要 |
|---|---|
| [アーキテクチャ定義](./ArchitectureDefinition.md) | katachi の DSL で書かれた役割の定義と、それを assert するテスト |
| [プロセッサ](./Processor.md) | このプロジェクトが自分で書いた ArchitectureProcessor。定義を読んで何かを作る |
| [生成ドキュメント](./GeneratedDocumentation.md) | この定義から書き出され、リポジトリにコミットされる Markdown |
| [レイアウトスナップショット](./LayoutSnapshot.md) | `layout { }` を平坦化した結果を記録したテキスト。katachi 自身の自己検証用 |

## このグループの配置

```
:architecture-test
  src/test/kotlin/com/example/
    ProjectArchitecture.kt      アーキテクチャ定義
    ProjectArchitectureTest.kt  アーキテクチャ定義
    *Spec.kt                    アーキテクチャ定義
    groups/*.kt                 アーキテクチャ定義
    roles/*.kt                  アーキテクチャ定義
    processors/*.kt             プロセッサ

docs/
  README.md                     生成ドキュメント
  **/*.md                       生成ドキュメント
snapshots/*.txt                 レイアウトスナップショット
```
