package com.example

import com.example.plugin.configureRouting
import com.example.plugin.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    configureSerialization()
    configureRouting()
}
