package com.example.kmp.groups

import com.example.kmp.roles.architectureDefinition
import com.example.kmp.roles.fake
import com.example.kmp.roles.generatedDocumentation
import com.example.kmp.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Test doubles, the test code itself, the architecture definition it checks, and the pages
 * that definition generates.
 *
 * `ArchitectureDefinition` is a role of its own rather than a corner of `Test`, because the
 * definition is not test code: it describes the project, and the test that asserts it is one
 * line. Giving it a role is also what keeps `:architecture-test` from being a directory nobody
 * declared.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト支援"
    summary = "テストダブル、テストコード本体、katachi のアーキテクチャ定義、そこから生成されるドキュメント"
    description = """
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
    """.trimIndent()

    fake()
    test()
    architectureDefinition()
    generatedDocumentation()
}
