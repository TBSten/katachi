# 自作プロセッサのサンプル ドキュメント

katachi の定義を読む processor を、利用者が自分で書く方法だけを見せるサンプル。このページ以下はすべて `--processor=docs` が生成したもので、手では書かない。

## Document map

### [本体](./core/README.md)

ノートを読み出して並べるだけの小さなアプリ。processor が読む対象

- [エントリポイント](./core/Entrypoint.md)
- [モデル](./core/Model.md)
- [保管庫](./core/Store.md)

### [定義とプロセッサ](./testing/README.md)

katachi の定義、それを読む自作プロセッサ3本、そして生成されたドキュメント

- [アーキテクチャ定義](./testing/ArchitectureDefinition.md)
- [プロセッサ](./testing/Processor.md)
- [生成ドキュメント](./testing/GeneratedDocumentation.md)
- [レイアウトスナップショット](./testing/LayoutSnapshot.md)

## 本体

ノートを読み出して並べるだけの小さなアプリ。processor が読む対象

- [エントリポイント](./core/Entrypoint.md) ... プロセスの起動。`main()` を持つ唯一のファイル
- [モデル](./core/Model.md) ... アプリが扱う値。data class・enum・値オブジェクトを置く
- [保管庫](./core/Store.md) ... 値がどこから来るかを引き受ける。いまはメモリ上の固定値

## 定義とプロセッサ

katachi の定義、それを読む自作プロセッサ3本、そして生成されたドキュメント

- [アーキテクチャ定義](./testing/ArchitectureDefinition.md) ... katachi の DSL で書かれた役割の定義と、それを assert するテスト
- [プロセッサ](./testing/Processor.md) ... このプロジェクトが自分で書いた ArchitectureProcessor。定義を読んで何かを作る
- [生成ドキュメント](./testing/GeneratedDocumentation.md) ... この定義から書き出され、リポジトリにコミットされる Markdown
- [レイアウトスナップショット](./testing/LayoutSnapshot.md) ... `layout { }` を平坦化した結果を記録したテキスト。katachi 自身の自己検証用
