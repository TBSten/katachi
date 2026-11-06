@file:OptIn(ExperimentalKatachiApi::class)

package com.example.api

import com.example.allowedContents
import com.example.data.repository
import com.example.domain.model
import com.example.domain.service
import com.example.forbiddenContents
import com.example.testing.test
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.konsist.declarations.ktFileContent

fun DeclarationContainerScope.controller() = "Controller".role {
    title = "Controller"
    summary = "Receives one HTTP request, calls the matching Service and returns the result"
    description = """
        The boundary between HTTP and the inside of the application. It owns registering paths and
        methods, pulling values out of the request, and turning what a Service returned into a
        response.

        One controller per file, and the file name is `<Resource>Controller.kt`. Each resource
        gets one package such as `controller/health/`, and the controller goes inside it, named
        after the directory: `controller/health/HealthController.kt`. The file name directly
        names a group of endpoints, and the directory decides it, so the name alone tells you
        whether a new path belongs in an existing file or in a new one. `layout { }` only looks
        at where files live and what they are named; `ktFileContent { }` adds what a file holds:
        the package of its resource, a class with `register(route: Route)`, and imports that
        leave out `com.example.repository`. The rest of "Forbidden contents" is not rejected
        mechanically.

        It can be generated from a template. The package level of the resource is named
        `resource` and the file name is derived from it, so `--arg template=Controller --arg
        resource=user` produces `controller/user/UserController.kt`. `resource` goes straight
        into the package, so the template accepts only letters and digits (a value such as
        `--arg resource=user-profile` is rejected).
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
    // The directory comes from the layout: `--arg resource=...` fills in `capture("resource")`,
    // and the file name is derived from the same value.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=Controller --arg resource=user
    layout {
        // The application is the root project. What the chain says is the same tree as
        // `src/main/kotlin/com/example/controller` spelled out by hand: `mainSourceSet` is
        // `src/main`, `kotlin` is the directory of that name, and the package is written out.
        // `capture("resource")` is a `*` with a name: one package level per resource. The file
        // name is that name in PascalCase, so `controller/health/` holds `HealthController.kt`
        // and the check reports any other `*Controller.kt` there.
        mainSourceSet / kotlin / "com/example/controller" / capture("resource") /
                "${capture("resource").pascalCase}Controller".ktFile()
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
                        // The same conversion the layout applies to the file name.
                        val name = resource.pascalCase
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
    // The block runs once per file, so `captureValue("resource")` is that file's own directory
    // (`health` for `controller/health/HealthController.kt`) and the package follows it.
    ktFileContent {
        `package`("com.example.controller.${captureValue("resource")}")

        // HTTP and the domain. No com.example.repository: a Controller does not fetch data itself.
        `import`("io.ktor.server.**")
        `import`("com.example.service.**")
        `import`("com.example.model.**")

        required(`class`, fileName) {
            allow(private, `val`, "*", "*Service")      // the Services it calls, swappable from a test
            required(`fun`, "register")("route: Route") // the one function plugin/Routing.kt calls
        }
    }
}
