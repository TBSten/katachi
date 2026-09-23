package com.example.groups

import com.example.roles.controller
import com.example.roles.ktorPlugin
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the API layer: everything that faces HTTP. */
fun DeclarationContainerScope.apiGroup() = "api".group {
    title = "API"

    controller()
    ktorPlugin()
}
