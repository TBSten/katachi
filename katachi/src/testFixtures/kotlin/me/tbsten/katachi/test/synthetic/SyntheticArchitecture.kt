package me.tbsten.katachi.test.synthetic

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.FileSelection
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * The `architecture { }` that describes this project: one group, one role per
 * [SyntheticProject.roles].
 *
 * The first role also declares the skeleton: the root project's `settings.gradle.kts` and
 * `build.gradle.kts`, every module's `build.gradle.kts`, and the ignored `build/` of each. It
 * is declared once, by one role, since two roles declaring one file is an `AmbiguousLayout`.
 * A module role writes `"modules" / "*" { }` and a fixed role writes `"shared/roleN" { }`,
 * each allowing `*RoleN.kt` directly inside its directory.
 *
 * @param files which files the check looks at. The whole tree by default: the in-memory tree
 *   has no git to ask, and a directory written by [writeTo] is not a repository.
 */
fun SyntheticProject.architecture(files: FileSelection = FileSelection.WholeTree): Architecture {
    val roles = roles
    return architecture {
        this.files = files
        "synthetic".group {
            for ((index, role) in roles.withIndex()) {
                role.name.role {
                    layout {
                        if (index == 0) {
                            "settings.gradle".ktsFile()
                            "build.gradle".ktsFile()
                            "build".ignore()
                            "modules/*" {
                                "build.gradle".ktsFile()
                                "build".ignore()
                            }
                        }
                        when (role.placement) {
                            SyntheticPlacement.Module -> "modules/*" {
                                description = "${role.name} of every module."
                                mainSourceSet / kotlin / role.directory / "*${role.name}".ktFile()
                            }

                            SyntheticPlacement.Fixed -> "shared/${role.directory}" {
                                "*${role.name}".ktFile()
                            }
                        }
                    }
                }
            }
        }
    }
}
