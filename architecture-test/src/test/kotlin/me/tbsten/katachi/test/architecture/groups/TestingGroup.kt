package me.tbsten.katachi.test.architecture.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.roles.architectureDefinition
import me.tbsten.katachi.test.architecture.roles.spec
import me.tbsten.katachi.test.architecture.roles.specSupport

/**
 * The roles of the test code, including the architecture definition itself.
 *
 * ## Why the tests are not split by layer
 *
 * The test sources of `:katachi` mirror the production packages — `fs`, `dsl`, `check` — so
 * layers would have been the easy split. They would also have said nothing: a test may import
 * anything it likes, so there is no direction to enforce and no file that would land in the
 * wrong one. The boundary that is real here is a different one, and it is written in the file
 * names: a test (`*Spec.kt`) and the scaffolding a test uses. So these roles are cut by name.
 *
 * The definition itself is a third role rather than part of either: `ArchitectureDefinition`
 * describes the shape, `Spec` asserts behaviour.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト"

    spec()
    specSupport()
    architectureDefinition()
}
