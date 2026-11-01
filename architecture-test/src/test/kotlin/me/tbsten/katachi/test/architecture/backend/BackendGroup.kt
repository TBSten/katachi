package me.tbsten.katachi.test.architecture.backend

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.backendGroup() = "backend".group {
    title = "バックエンド"

    konsistBackend()
    kontureBackend()
}
