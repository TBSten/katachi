[katachi-sample-kmp](../README.md)

# テスト支援

テストダブル、テストコード本体、katachi のアーキテクチャ定義、そこから書き出されるドキュメントとスナップショット、棚上げした違反の台帳

テストにまつわる6つの役割です。Fake（`:testing` の commonMain）、Test（各モジュールの
テスト）、ArchitectureDefinition（`:architecture-test`）、GeneratedDocumentation
（ルート直下の `docs/`）、LayoutSnapshot（ルート直下の `snapshots/`）、Baseline
（ルート直下の `katachi-baseline.json`。棚上げした違反の台帳）。

ArchitectureDefinition を Test と分けてあるのが、この group でいちばん言いたいことです。
アーキテクチャ定義はテストコードではありません。プロジェクトの形を説明するのが仕事で、
それを実際のディレクトリと突き合わせるテストは1行しかない。役割を与えておかないと、
`:architecture-test` が誰も宣言していないディレクトリになります。

Fake が `commonTest` ではなく `commonMain` にいるのも、この group が見せている形です。
テスト source set は他のモジュールから参照できないので、テストのためのコードでも
production の source set に置かざるをえないことがあります。`:testing` はそのために
切られた、アプリ本体から誰も依存しないモジュールです。

GeneratedDocumentation と LayoutSnapshot を ArchitectureDefinition と分けてあるのも
同じ理由です。定義は人が書き、`docs/` は `katachiDocs` が、`snapshots/` は
`:architecture-test:test` が書く。手を入れてよい場所が逆なので、1つの役割にまとめると
「どちらを直せばいいのか」が言えなくなります。`build/` の外に置いた生成物にも役割が
要る、という例にもなっています。

書き出されたもの同士も分けてあります。読み手が違うからです。`docs/` は定義を読みに
来た人が開くページ、`snapshots/` は定義を書き換えた差分をレビューする人が見る
テキストです。

| Role | Summary |
|---|---|
| [フェイク](./Fake.md) | :testing の commonMain に置く偽の実装。他モジュールのテストから使う |
| [テストコード](./Test.md) | 各モジュールのテスト。KMP モジュールは commonTest、純 Android / 純 JVM モジュールは src/test |
| [アーキテクチャ定義](./ArchitectureDefinition.md) | :architecture-test モジュールの src/test。katachi の DSL で書いたこのプロジェクトの定義。どのレイヤーにも属さないので専用モジュールに置く |
| [生成ドキュメント](./GeneratedDocumentation.md) | この定義から書き出され、リポジトリにコミットされる Markdown |
| [レイアウトのスナップショット](./LayoutSnapshot.md) | この定義を平坦化して全行書き出した記録。定義の変化を人が差分でレビューするためにある |
| [baseline（棚上げした違反の台帳）](./Baseline.md) | katachi を入れた時点ですでにあった違反を記録し、テストを落とさずに棚上げしておく台帳 |

## Placement in this group

```
:testing
  src/commonMain/kotlin/**/Fake*.kt             フェイク

:app:android
  src/test/kotlin/com/example/kmp/app/*Spec.kt  テストコード

:architecture-test
  src/test/kotlin/com/example/kmp/
    *.kt                                        アーキテクチャ定義
    */*.kt                                      アーキテクチャ定義

docs/
  README.md                                     生成ドキュメント
  **/*.md                                       生成ドキュメント
snapshots/layout.txt                            レイアウトのスナップショット
katachi-baseline.json                           baseline（棚上げした違反の台帳）
```

## 置いてはいけないもの

- アプリ本体のコード。ここにあるのは「テストのための」コードと、プロジェクトの説明です
