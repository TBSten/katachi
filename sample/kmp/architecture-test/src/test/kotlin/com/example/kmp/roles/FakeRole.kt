package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** Test doubles other modules' tests reach for, gathered in the `:testing` module. */
fun DeclarationContainerScope.fake() = "Fake" {
    title = "フェイク"
    summary = ":testing の commonMain に置く偽の実装。他モジュールのテストから使う"
    description = """
        他のモジュールのテストが使うテストダブルを `:testing` に集めた役割です。ファイル名は
        `Fake*.kt` に限られるので、`StubUserRepository.kt` や `TestUserRepository.kt` は
        通りません。呼び名を1つに固定しておくと、テストコードを読むときに「これは偽物だ」が
        名前だけで分かります。

        `commonTest` ではなく `commonMain`、つまり production の source set に置いてあるのが
        この役割の要点です。テスト source set は他のモジュールから参照できないので、
        `:app:android` のテストから使いたければ production 側に出すしかありません。
        `:testing` は `api(project(":data"))` だけに依存する、アプリ本体から誰も依存しない
        モジュールとして切ってあります。
    """.trimIndent()
    allowedContents = """
        - `:data` のインターフェースを満たす偽実装。返す値はコンストラクタで差し替えられる形に
          しておきます（`FakeUserRepository(listOf("alice"))` のように）
    """.trimIndent()
    forbiddenContents = """
        - テストそのもの。`*Spec.kt` は Test の役割です
        - production から呼ばれる実装。本物は `:data` の `*Impl` です
        - kotest などテストフレームワークへの依存。ここは production の source set なので、
          持ち込むとアプリ本体のビルドに紛れ込みます
    """.trimIndent()
    example("FakeUserRepository", "UserRepository の偽実装")
    layout {
        ":testing".module {
            "commonMain".sourceSet / kotlin / modulePackage / "Fake*".ktFile()
        }
    }
}
