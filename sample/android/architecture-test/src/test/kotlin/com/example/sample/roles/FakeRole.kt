package com.example.sample.roles

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of a stand-in implementation other modules' tests use.
 *
 * `:testing` follows the module path convention, so its package comes from `modulePackage`
 * rather than being written out the way `:architecture-test` has to write its own.
 */
fun DeclarationContainerScope.fake() = "Fake" {
    title = "フェイク"
    summary = ":testing に置く、他モジュールのテストから使う偽の実装"
    example("FakeUserRepository", "UserRepository のメモリ実装")
    layout {
        ":testing".module {
            mainSourceSet / kotlin / modulePackage / "Fake*".ktFile()
        }
    }
}
