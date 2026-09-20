package me.tbsten.katachi.test.dsl

import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.GroupScope

/**
 * Extension functions used to prove that a definition can be split across files, and that
 * the declaration sites then point at THIS file rather than at the spec that calls them.
 *
 * WARNING: DeclarationSiteSpec asserts the exact line numbers of the declarations below.
 * Adding or removing a line here breaks that spec; fix the expectations there too.
 */
fun ArchitectureScope.domainRoles() {
    "domain".group {
        title = "ドメイン"
        "UseCase" {
            title = "ユースケース"
            summary = "各画面で発生するアプリ固有の1つの振る舞い"
            example("GetUserUseCase", "ユーザーを取得する")
            layout { }
        }
        modelGroup()
    }
}

/** Nested split: a [GroupScope] extension called from inside another group's block. */
fun GroupScope.modelGroup() {
    "model".group {
        "Entity" { }
    }
}

/** Declared but deliberately never called by some specs, to show the silent-drop case. */
fun ArchitectureScope.dataRoles() {
    "data".group {
        "Repository" { }
    }
}
