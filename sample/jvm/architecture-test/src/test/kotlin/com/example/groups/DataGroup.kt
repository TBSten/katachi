package com.example.groups

import com.example.roles.repository
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the data layer: where the values come from. */
fun DeclarationContainerScope.dataGroup() = "data".group {
    title = "データ"

    repository()
}
