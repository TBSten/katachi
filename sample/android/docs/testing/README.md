[katachi-sample-android](../README.md)

# テスト

アプリを確かめるためにあるもの。共有のフェイク、テスト、アーキテクチャ定義、生成ドキュメント、レイアウトのスナップショット

アプリのレイヤーではなく、アプリを確かめるためにあるもの。5つ集めてある。
フェイクは `:testing` にある差し替え用の実装、テストコードはテストそのもの、
アーキテクチャ定義は katachi の DSL で書かれたこの定義自身、生成ドキュメントは
その定義から `katachiDocs` が書き出す `docs/`、レイアウトのスナップショットは
同じ定義を平坦化して `:architecture-test:test` が書き出す `snapshots/`。

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

生成ドキュメントとスナップショットはモジュールの中ですらなく、ルート直下の `docs/` と
`snapshots/` に出る。`build/` の外にある生成物にも役割が要る、という例としてここに
置いてある。2つを1つにまとめていないのは読み手が違うからで、`docs/` は定義を読みに来た
人が開くページ、`snapshots/` は定義を書き換えた差分をレビューする人が見るテキスト。
人が書くルートの `README.md` はどちらとも別扱いで、`tool` グループの `Documentation`
役割の側。

| Role | Summary |
|---|---|
| [フェイク](./Fake.md) | :testing に置く、他モジュールのテストから使う偽の実装 |
| [テストコード](./Test.md) | 各モジュールの src/test/kotlin に置くテストそのもの |
| [アーキテクチャ定義](./ArchitectureDefinition.md) | katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない |
| [生成ドキュメント](./GeneratedDocumentation.md) | この定義から書き出され、リポジトリにコミットされる Markdown |
| [レイアウトのスナップショット](./LayoutSnapshot.md) | この定義を平坦化して全行書き出した記録。定義の変化を人が差分でレビューするためにある |

## Placement in this group

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
snapshots/layout.txt           レイアウトのスナップショット
```
