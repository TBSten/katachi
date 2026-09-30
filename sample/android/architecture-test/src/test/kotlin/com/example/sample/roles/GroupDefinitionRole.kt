@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/** The role of a file that declares one group of the definition. */
fun DeclarationContainerScope.groupDefinition() = "GroupDefinition" {
    title = "Group definition"
    summary = "groups/<Name>Group.kt, one group of the definition, which lists its roles by calling their functions"
    description = """
        One group of the definition per file, in `groups/<Name>Group.kt`, and the file name
        says which group it is. A group says what it is made of by calling the role functions
        in order. Only `*Group.kt` may sit in `groups/`, so a shared helper slipping in as
        another kind of file becomes an `[UnexpectedFile]`.

        `:architecture-test` is a plain `kotlin("jvm")` module that belongs to no layer of the
        app. It is not an Android module because katachi is a JVM library and the definition can
        take the same shape whatever the project type. Its package cannot be derived from the
        module path (applied as is it would become `com/example/sample/architectureTest`), so
        the roles of this module write `com/example/sample` directly.

        Not called from anywhere on its own: add the call to `ProjectArchitecture.kt` once the
        group says something.
    """.trimIndent()
    example("groups/UiGroup.kt", "The declaration of the ui group")
    // A new group, as a skeleton to fill in.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=testing.GroupDefinition --arg name=Domain
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/sample" {
                "groups" {
                    "${capture("name")}Group".ktFile()
                        .template {
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
            }
        }
    }
}
