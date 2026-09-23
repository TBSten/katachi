package com.example.sample.groups

import com.example.sample.roles.architectureDefinition
import com.example.sample.roles.fake
import com.example.sample.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles that exist for testing: the shared fakes in `:testing`, the tests themselves, and
 * the architecture definition.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own
 * rather than hiding inside `Test`: `ArchitectureDefinition` describes the shape, `Test`
 * asserts behaviour. The two share one module and are told apart by where the file sits:
 * the definition is `ProjectArchitecture.kt` plus the `Group.kt` and `Role.kt` files of the
 * `groups` and `roles` packages, and everything `*Spec.kt` or `*Test.kt` at the top of the
 * package is a test.
 *
 * `:architecture-test` is also the second module whose package does not follow its module
 * path — it would come out as `com/example/sample/architectureTest` — so the two roles
 * write `com/example/sample` out as a key. `:testing` does follow it and uses
 * `modulePackage`, which is what makes the difference visible side by side.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト"

    fake()
    test()
    architectureDefinition()
}
