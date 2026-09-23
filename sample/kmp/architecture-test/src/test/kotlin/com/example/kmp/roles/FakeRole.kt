package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** Test doubles other modules' tests reach for, gathered in the `:testing` module. */
fun DeclarationContainerScope.fake() = "Fake" {
    title = "フェイク"
    summary = ":testing の commonMain に置く偽の実装。他モジュールのテストから使う"
    example("FakeUserRepository", "UserRepository の偽実装")
    layout {
        ":testing".module {
            "commonMain".sourceSet / kotlin / modulePackage / "Fake*".ktFile()
        }
    }
}
