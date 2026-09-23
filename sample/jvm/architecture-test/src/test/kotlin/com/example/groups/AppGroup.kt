package com.example.groups

import com.example.roles.entrypoint
import com.example.roles.serverConfig
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles that assemble and configure the running process. */
fun DeclarationContainerScope.appGroup() = "app".group {
    title = "アプリケーション"

    entrypoint()
    serverConfig()
}
