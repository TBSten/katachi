[自作プロセッサのサンプル](../README.md) / [定義とプロセッサ](README.md)

# プロセッサ

このプロジェクトが自分で書いた ArchitectureProcessor。定義を読んで何かを作る

katachi の定義を読んで何かを作る、このプロジェクト自身のコードです。このサンプルが
見せたいのはここ1つで、`src/main/kotlin` にある本体はこれらが読む対象を用意するために
だけ置いてあります。

3本あり、それぞれ違う形です。

- `RoleFileCount` — 引数なし。`ArchitectureProcessorNoArg` を `object` で実装し、
  `context.roles` と `context.filesOf(role)` からファイル数を数える
- `RoleTable` — 型付きの引数。`@Serializable data class Args` を持ち、`--arg` から
  `String` / `List<String>` / `Int` / enum を受ける
- `RoleDocCoverage` — 検査。`List<Violation>` ではなく自前の `Report` を答えにし、
  問題があれば `Result.failure` を返して `katachiRoleDocCoverage` を落とす

置いてよいのは `ArchitectureProcessor` の実装と、その引数・結果の型だけです。

置いてはいけないもの:

- 役割や group の宣言。それらはアーキテクチャ定義の役割です。定義は形を書くもの、
  processor はその形を読むもので、混ぜると「どちらが先に決まるのか」が読めなくなります
- アプリのコード。processor はビルド時に走るもので、`main()` からは呼ばれません

`architecture-test/build.gradle.kts` の `katachi { processors { register(...) } }` に
3本とも登録してあり、登録キーごとの `katachi<Key>` タスク（`katachiRoleTable` など）で実行できます。登録を忘れた processor は
コマンドラインからは呼べませんが、テストから `projectArchitecture.process(...)` で
呼ぶぶんには登録は要りません。

`layout { }` は `processors` パッケージ直下の `.kt` を認めます。ファイル名は
縛っていません（1ファイル1 processor はこの文章にある約束で、機械的には弾かれません）。

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/processors/*.kt` |  |

## 例

- `RoleFileCount` ... 引数なしの最小形
- `RoleTable` ... 型付き引数を取る形
- `RoleDocCoverage` ... Result.failure で run を落とす検査
