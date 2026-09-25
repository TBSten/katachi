[katachi-sample-kmp](../README.md)

# テスト支援

テストダブル、テストコード本体、katachi のアーキテクチャ定義、そこから生成されるドキュメント

テストにまつわる4つの役割です。Fake（`:testing` の commonMain）、Test（各モジュールの
テスト）、ArchitectureDefinition（`:architecture-test`）、GeneratedDocumentation
（ルート直下の `docs/`）。

ArchitectureDefinition を Test と分けてあるのが、この group でいちばん言いたいことです。
アーキテクチャ定義はテストコードではありません。プロジェクトの形を説明するのが仕事で、
それを実際のディレクトリと突き合わせるテストは1行しかない。役割を与えておかないと、
`:architecture-test` が誰も宣言していないディレクトリになります。

Fake が `commonTest` ではなく `commonMain` にいるのも、この group が見せている形です。
テスト source set は他のモジュールから参照できないので、テストのためのコードでも
production の source set に置かざるをえないことがあります。`:testing` はそのために
切られた、アプリ本体から誰も依存しないモジュールです。

GeneratedDocumentation を ArchitectureDefinition と分けてあるのも同じ理由です。
定義は人が書き、`docs/` は `--processor=docs` が書く。手を入れてよい場所が逆なので、
1つの役割にまとめると「どちらを直せばいいのか」が言えなくなります。`build/` の外に
置いた生成物にも役割が要る、という例にもなっています。

置いてはいけないもの:

- アプリ本体のコード。ここにあるのは「テストのための」コードと、プロジェクトの説明です

| 役割 | 概要 |
|---|---|
| [フェイク](./Fake.md) | :testing の commonMain に置く偽の実装。他モジュールのテストから使う |
| [テストコード](./Test.md) | 各モジュールのテスト。KMP モジュールは commonTest、純 Android / 純 JVM モジュールは src/test |
| [アーキテクチャ定義](./ArchitectureDefinition.md) | :architecture-test モジュールの src/test。katachi の DSL で書いたこのプロジェクトの定義。どのレイヤーにも属さないので専用モジュールに置く |
| [生成ドキュメント](./GeneratedDocumentation.md) | この定義から書き出され、リポジトリにコミットされる Markdown |

## このグループの配置

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
```
