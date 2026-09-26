[katachi-sample-android](../README.md) / [テスト](README.md)

# テストコード

各モジュールの src/test/kotlin に置くテストそのもの

テストそのもの。このサンプルでテストを持つモジュールは `:architecture-test` だけで、
アプリ側の各モジュールはそこに書かれた定義を通して検査される。

ファイル名が `*Spec.kt` か `*Test.kt` で、package の直下（`groups/` `roles/` の外）に
あるものがテスト。同じモジュールにあるアーキテクチャ定義役割とはこの2点で区別され、
`roles/` にテストを置けば `[UnexpectedFile]` になる。逆にテストの隣へ
定義のヘルパーを置くこともできない。

いま4つある。利用者が書くのは `ProjectArchitectureTest` だけで、
`projectArchitecture.assert()` を呼ぶ JUnit のテスト1つ。残りは katachi 自身の検証で、
`ProjectArchitectureSpec` が組み上がった定義を確かめ、`LayoutSnapshotSpec` が
平坦化した layout をスナップショットと突き合わせ、`ProjectRootSpec` が
プロジェクトルートの探索結果を見張る。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/sample/*Spec.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/sample/*Test.kt` |  |

## Examples

- `ProjectArchitectureTest` ... 利用者が書く唯一のテスト
- `ProjectArchitectureSpec` ... この定義そのものを検証するテスト

## 置いてはいけないもの

テストから使う道具。他モジュールのテストへ渡す差し替え実装は
`:testing` のフェイク役割にあり、`src/test` からは公開できない。
