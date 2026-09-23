package com.example.kmp.groups

import com.example.kmp.roles.architectureDefinition
import com.example.kmp.roles.fake
import com.example.kmp.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Test doubles, the test code itself, and the architecture definition it checks.
 *
 * `ArchitectureDefinition` is a role of its own rather than a corner of `Test`, because the
 * definition is not test code: it describes the project, and the test that asserts it is one
 * line. Giving it a role is also what keeps `:architecture-test` from being a directory nobody
 * declared.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト支援"

    fake()
    test()
    architectureDefinition()
}
