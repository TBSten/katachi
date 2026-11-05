@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.testing

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.diff.diffWeight
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

fun DeclarationContainerScope.groupDefinition() = "GroupDefinition".role {
    title = "Group definition"
    summary = "<group>/<Name>Group.kt, one group of the definition, which lists its roles by calling their functions"
    description = """
        One group of the definition per file, in `<group>/<Name>Group.kt`, and the file name
        says which group it is. The directory is the group's name as written in
        `"ui".group { }`, lower case and joined (`ui/UiGroup.kt`), and a group inside a group
        sits in a directory inside it. A group says what it is made of by calling the role
        functions in order, and the roles it calls sit beside it in that directory. A file that
        is not a `*Group.kt` or `*Role.kt` in there, such as a shared helper, becomes an
        `[UnexpectedFile]`.

        `:architecture-test` is a plain `kotlin("jvm")` module that belongs to no layer of the
        app. It is not an Android module because katachi is a JVM library and the definition can
        take the same shape whatever the project type. Its package cannot be derived from the
        module path (applied as is it would become `com/example/sample/architectureTest`), so
        the roles of this module write `com/example/sample` directly.

        Not called from anywhere on its own: add the call to `ProjectArchitecture.kt` once the
        group says something.
    """.trimIndent()
    example("ui/UiGroup.kt", "The declaration of the ui group")
    // A new group, as a skeleton to fill in.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=testing.GroupDefinition --arg name=Domain --arg dir=domain
    // A change to the definition changes what every check and page says, so katachiDiff
    // puts it at the top of the heat.
    diffWeight = 10.0
    layout {
        "architecture-test" {
            testSourceSet / kotlin / "com/example/sample" {
                // Any depth: `ui/UiGroup.kt`, and `app/api/ApiGroup.kt` for a group inside a group.
                "**" / "*Group".ktFile()
                // The skeleton goes to the top level. `dir` is the group's name in lower case
                // (`--arg dir=domain`): a directory is a package, and a package has no capitals.
                capture("dir") {
                    "${capture("name")}Group".ktFile()
                        .template {
                            val name = captureValue("name")
                            val dir = captureValue("dir")
                            val function = name.replaceFirstChar { it.lowercaseChar() }
                            """
                                package com.example.sample.$dir

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
