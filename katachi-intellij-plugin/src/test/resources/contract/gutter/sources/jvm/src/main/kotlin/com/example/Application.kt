package com.example

import com.example.plugin.configureRouting
import com.example.plugin.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

/** Entry point. The engine reads `src/main/resources/application.conf`. */
fun main(args: Array<String>) {
    EngineMain.main(args)
}

/**
 * The application module referenced from `application.conf`.
 *
 * `testApplication { }` does not apply the modules listed in the configuration file, so
 * the tests call this function explicitly.
 */
@Suppress("unused")
fun Application.module() {
    configureSerialization()
    configureRouting()
}
