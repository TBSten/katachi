@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/** The role of a file that declares one role of the definition. */
fun DeclarationContainerScope.roleDefinition() = "RoleDefinition" {
    title = "Role definition"
    summary = "roles/<Name>Role.kt, one role of the definition, with where its files live"
    description = """
        One role of the definition per file, in `roles/<Name>Role.kt`, and the file name says
        which role it is. Only `*Role.kt` may sit in `roles/`, so a shared helper slipping in
        as another kind of file becomes an `[UnexpectedFile]`.

        `:architecture-test` is a plain `kotlin("jvm")` module that belongs to no layer of the
        app. It is not an Android module because katachi is a JVM library and the definition can
        take the same shape whatever the project type. Its package cannot be derived from the
        module path (applied as is it would become `com/example/sample/architectureTest`), so
        the roles of this module write `com/example/sample` directly.

        Not called from anywhere on its own: add the call to the group this role belongs to
        once it says something.
    """.trimIndent()
    example("roles/ScreenRole.kt", "The declaration of the Screen role")
    // A new role, as a skeleton to fill in.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=testing.RoleDefinition --arg name=UseCase
    layout {
        "architecture-test" {
            testSourceSet / kotlin / "com/example/sample" {
                "roles" {
                    "${capture("name")}Role".ktFile()
                        .template {
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
