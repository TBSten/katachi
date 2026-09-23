package com.example.groups

import com.example.roles.architectureDefinition
import com.example.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the test code, including the architecture definition itself.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own rather
 * than hiding inside `Test`: `ArchitectureDefinition` describes the shape, `Test` asserts
 * behaviour.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト"

    test()
    architectureDefinition()
}
