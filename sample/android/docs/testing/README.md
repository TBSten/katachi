[katachi-sample-android](../README.md)

# テスト

アプリを確かめるためにあるもの。共有のフェイク、テスト、アーキテクチャ定義、生成ドキュメント

アプリのレイヤーではなく、アプリを確かめるためにあるもの。4つ集めてある。
フェイクは `:testing` にある差し替え用の実装、テストコードはテストそのもの、
アーキテクチャ定義は katachi の DSL で書かれたこの定義自身、生成ドキュメントは
その定義から `--processor=docs` が書き出す `docs/`。

アーキテクチャ定義をテストコードに混ぜていないのは、2つが別のことを書いているから。
定義は「どんな形をしているか」、テストは「どう振る舞うか」。どちらも
`:architecture-test` にあり、ファイルの場所と名前で区別される
（`ProjectArchitecture.kt` と `groups/` `roles/` が定義、package 直下の
`*Spec.kt` `*Test.kt` がテスト）。定義を書き換える人とテストを足す人は
だいたい別のことをしているので、ドキュメント上でも別項目にしてある。

`:testing` だけがアプリ側のモジュールで、`:data` のインターフェースを満たす `Fake*` を
`main` ソースセットに置いて他モジュールのテストへ公開する。テスト用のコードを
あえて製品コードとして出すのは、`src/test` が他モジュールから見えないため。

`:architecture-test` は `:app` と並ぶ、package がモジュールパスから導けないモジュール。
そのまま当てると `com/example/sample/architectureTest` になるので、この group の
定義側の2つの役割はどちらも `com/example/sample` を直接書いている。

生成ドキュメントだけはモジュールの中ですらなく、リポジトリのルート直下の `docs/` に出る。
`build/` の外にある生成物にも役割が要る、という例としてここに置いてある。
人が書くルートの `README.md` は別扱いで、`tool` グループの `Documentation` 役割の側。

| 役割 | 概要 |
|---|---|
| [フェイク](./Fake.md) | :testing に置く、他モジュールのテストから使う偽の実装 |
| [テストコード](./Test.md) | 各モジュールの src/test/kotlin に置くテストそのもの |
| [アーキテクチャ定義](./ArchitectureDefinition.md) | katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない |
| [生成ドキュメント](./GeneratedDocumentation.md) | この定義から書き出され、リポジトリにコミットされる Markdown |

## このグループの配置

```
:testing
  src/main/kotlin/**/Fake*.kt  フェイク

:architecture-test
  src/test/kotlin/com/example/sample/
    *Spec.kt                   テストコード
    *Test.kt                   テストコード
    ProjectArchitecture.kt     アーキテクチャ定義
    groups/*Group.kt           アーキテクチャ定義
    roles/*Role.kt             アーキテクチャ定義

docs/
  README.md                    生成ドキュメント
  **/*.md                      生成ドキュメント
```
