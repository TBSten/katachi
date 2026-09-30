@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/** The role of the katachi definition itself, which belongs to no layer of the application. */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "Architecture definition"
    summary = "The role definitions written in the katachi DSL. Belongs to no layer"
    description = """
        This definition itself, written in the katachi DSL. It lives in `:architecture-test`, a
        plain `kotlin("jvm")` module that belongs to no layer of the app. It is not an Android
        module because katachi is a JVM library and the definition can take the same shape
        whatever the project type.

        One declaration per file, and the file name says which kind it is.
        `ProjectArchitecture.kt` is the entrypoint and only calls the group functions,
        `groups/<Name>Group.kt` holds one group, and `roles/<Name>Role.kt` holds one role. Only
        `*Group.kt` and `*Role.kt` may sit in `groups/` and `roles/`, so a shared helper
        slipping in as a third kind becomes an `[UnexpectedFile]`. The one exception is
        `DocumentSections.kt`, which gathers just the section definitions every group and role
        uses with `by`, and is allowed by name, like `ProjectArchitecture.kt`.

        The extension functions must not be `inline`. katachi takes the declaration site from
        the stack trace, so inlining would point at a line in the caller's file that nobody
        wrote. `ProjectArchitectureSpec` guards this by reading that line back.

        `:architecture-test`, like `:app`, is a module whose package cannot be derived from the
        module path. Applied as is it would become `com/example/sample/architectureTest`, so
        this role and the test-code role both write `com/example/sample` directly. There is no
        point applying the app's package rule to something that is not part of the app.

        It shares a module with the tests, but the two say different things. The definition says
        "what shape it has" and the tests say "how it behaves". They are told apart by file
        location and name.
    """.trimIndent()
    example("ProjectArchitecture.kt", "The entrypoint of the definition")
    example("roles/ScreenRole.kt", "The declaration of the Screen role")
    // A new role or group, as a skeleton to fill in. It is not called from anywhere yet:
    // add the call to a group (or to `ProjectArchitecture.kt`) once it says something.
    // `groups/` and `roles/` each carry their own id (`group` / `role`) rather than a `kind`
    // parameter, because which one to write is which file declaration was chosen, not a value.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=testing.ArchitectureDefinition.role --arg name=UseCase
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=testing.ArchitectureDefinition.group --arg name=Domain
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/sample" {
                "ProjectArchitecture".ktFile()
                // The one shared helper this definition allows: the section headings every
                // group and role writes through, named exactly for the same reason
                // `ProjectArchitecture.kt` is.
                "DocumentSections".ktFile()
                // One declaration per file, and the file name says which kind it is:
                // `groups/` holds `*Group.kt` and `roles/` holds `*Role.kt`, so a helper
                // dropped into either is reported as `[UnexpectedFile]` rather than
                // quietly becoming a third kind of file.
                "groups" {
                    "${capture("name")}Group".ktFile()
                        .template(id = "group") {
                            val name = captureValue("name")
                            val function = name.replaceFirstChar { it.lowercaseChar() }
                            """
                                package com.example.sample.groups

                                import me.tbsten.katachi.dsl.DeclarationContainerScope

                                // TODO: call ${function}Group() from ProjectArchitecture.kt.
                                /** TODO: say what the roles of the $name group have in common. */
                                fun DeclarationContainerScope.${function}Group() = "$function".group {
                                    title = "$name"
                                    summary = "TODO: what the roles of this group have in common"
                                }
                            """.trimIndent() + "\n"
                        }
                }
                "roles" {
                    "${capture("name")}Role".ktFile()
                        .template(id = "role") {
                            val name = captureValue("name")
                            val function = name.replaceFirstChar { it.lowercaseChar() }
                            """
                                package com.example.sample.roles

                                import me.tbsten.katachi.dsl.DeclarationContainerScope

                                // TODO: call $function() from the group this role belongs to.
                                /** TODO: say what a file of the $name role is. */
                                fun DeclarationContainerScope.$function() = "$name" {
                                    title = "$name"
                                    summary = "TODO: what a file of this role is"
                                    // TODO: declare where its files live.
                                    layout { }
                                }
                            """.trimIndent() + "\n"
                        }
                }
            }
        }
    }
}
