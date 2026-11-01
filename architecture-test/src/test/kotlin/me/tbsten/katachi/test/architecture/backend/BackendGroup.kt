package me.tbsten.katachi.test.architecture.backend

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The roles of the constraint backends — `:katachi-konsist` and `:katachi-konture`.
 *
 * A sibling module rather than a source set of `:katachi`, because `:katachi` has no runtime
 * dependencies at all and this one has Konsist as an `api` dependency. That is also why it is
 * its own group: the split is the library's most visible promise, and a reader of the
 * generated documentation should meet it as a boundary and not as one more layer.
 */
fun DeclarationContainerScope.backendGroup() = "backend".group {
    title = "バックエンド"

    konsistBackend()
    kontureBackend()
}
