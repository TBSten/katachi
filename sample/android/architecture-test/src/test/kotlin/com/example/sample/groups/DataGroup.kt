package com.example.sample.groups

import com.example.sample.roles.repository
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the data layer: what `:data` holds. */
fun DeclarationContainerScope.dataGroup() = "data".group {
    title = "データレイヤー"

    repository()
}
