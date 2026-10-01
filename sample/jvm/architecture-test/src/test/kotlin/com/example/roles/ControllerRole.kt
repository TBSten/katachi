@file:OptIn(ExperimentalKatachiApi::class)

package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/**
 * The role that faces HTTP: one request in, one service call, one response out.
 *
 * Each controller sits in a package of its own resource, `controller/health/HealthController.kt`,
 * and that level is `capture("resource")` rather than `"*"`. The check reads the two the same
 * way; the name is what lets the template below be told where to generate:
 * `--arg resource=user --arg name=User` writes `controller/user/UserController.kt`, and a
 * resource that has no package yet gets a new one.
 */
fun DeclarationContainerScope.controller() = "Controller" {
    title = "Controller"
    summary = "Receives one HTTP request, calls the matching Service and returns the result"
    description = """
        The boundary between HTTP and the inside of the application. It owns registering paths and
        methods, pulling values out of the request, and turning what a Service returned into a
        response.

        One controller per file, and the file name is `*Controller.kt`. Each resource gets one
        package such as `controller/health/`, and the controller goes inside it. The file name
        directly names a group of endpoints, so the name alone tells you whether a new path
        belongs in an existing file or in a new one. Note that `layout { }` only looks at where
        files live and what they are named; it does not mechanically reject "Forbidden contents".

        It can be generated from a template. The package level of the resource is named
        `resource`, so `--arg template=Controller --arg resource=user --arg name=User` produces
        `controller/user/UserController.kt`. `resource` goes straight into the package, so the
        template accepts only letters and digits (a value such as `--arg resource=user-profile`
        is rejected).
    """.trimIndent()
    allowedContents = """
        Only registration on a Ktor `Route` and the conversion needed to hand values over may be
        placed here. `HealthController` writes `route.get("/health") { ... }` inside
        `register(route: Route)`, and the `HealthService` it calls is received as a constructor
        argument with a default value, so a test can swap it.
    """.trimIndent()
    forbiddenContents = """
        - Branching or computation. The moment it decides "which one to return", it is the
          service's job
        - Calls to `com.example.repository`. A Controller does not fetch data directly
        - Application-wide configuration such as `install(...)`, and `routing { }` itself. Which
          Controllers are connected to the routing tree is decided by `plugin/Routing.kt`
    """.trimIndent()
    example("HealthController", "The health check entry point")
    // The directory comes from the layout: `--arg resource=...` fills in `capture("resource")`.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=Controller --arg resource=user --arg name=User
    layout {
        // The application is the root project. What the chain says is the same tree as
        // `src/main/kotlin/com/example/controller` spelled out by hand: `mainSourceSet` is
        // `src/main`, `kotlin` is the directory of that name, and the package is written out.
        // `capture("resource")` is a `*` with a name: one package level per resource.
        mainSourceSet / kotlin / "com/example/controller" / capture("resource") /
                "${capture("name")}Controller".ktFile()
                    .template {
                        val resource = captureValue("resource")
                        // `resource` goes straight into the package below, so a value with characters
                        // a package segment cannot hold (`user-profile`) would compile-fail rather
                        // than be caught here. While previewing (katachiTemplates), `resource` is the
                        // placeholder token, which is never alphanumeric, so `isPreview` lets it
                        // through instead of failing every preview.
                        require(isPreview || resource.all { it.isLetterOrDigit() }) {
                            "--arg resource=$resource: use letters and digits only"
                        }
                        val name = captureValue("name")
                        """
                            package com.example.controller.$resource

                            import io.ktor.server.response.respondText
                            import io.ktor.server.routing.Route
                            import io.ktor.server.routing.get

                            /** Maps `GET /$resource`. */
                            class ${name}Controller {
                                fun register(route: Route) {
                                    route.get("/$resource") {
                                        call.respondText("TODO: ${name}Controller")
                                    }
                                }
                            }
                        """.trimIndent() + "\n"
                    }
    }
}
