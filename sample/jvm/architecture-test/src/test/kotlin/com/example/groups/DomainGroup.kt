package com.example.groups

import com.example.roles.model
import com.example.roles.service
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the domain layer: the behaviour and the values the application is about. */
fun DeclarationContainerScope.domainGroup() = "domain".group {
    title = "ドメイン"

    service()
    model()
}
