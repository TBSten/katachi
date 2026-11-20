package me.tbsten.katachi.benchmark.util

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.FileSelection
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.konture.konture
import me.tbsten.katachi.test.synthetic.SyntheticPlacement
import me.tbsten.katachi.test.synthetic.SyntheticProject

// Every constraint here holds for every generated class: the benchmarks time the check, not a report.

internal fun SyntheticProject.konsistArchitecture(): Architecture = constrainedArchitecture {
    "classes are public".konsist {
        classes().must { it.hasPublicOrDefaultModifier }
    }
}

// Mirrors the class `generateProject()` writes, line for line, so nothing falls to `allow(anythingElse)`.
internal fun SyntheticProject.ktFileContentArchitecture(): Architecture = constrainedArchitecture {
    ktFileContent {
        `package`("synthetic.*")

        required(`class`, fileName) {
            required(`val`, "id").type("Int")
            required(`fun`, "describe")().returns("String")
        }
    }
}

internal fun SyntheticProject.kontureArchitecture(): Architecture = constrainedArchitecture {
    classesArePublicKonture()
}

// Konture's symbol table still reads every walked `.kt` file, while the rule covers the first role's files alone.
internal fun SyntheticProject.kontureOnFirstRoleArchitecture(): Architecture = constrainedArchitecture(firstRoleOnly = true) {
    classesArePublicKonture()
}

private fun RoleScope.classesArePublicKonture() {
    "classes are public".konture {
        classes { should().bePublic() }
    }
}

// The layout of `SyntheticProject.architecture()`, so the walk is the same whichever constraint is added.
private fun SyntheticProject.constrainedArchitecture(
    firstRoleOnly: Boolean = false,
    constraint: RoleScope.() -> Unit,
): Architecture {
    val roles = roles
    return architecture {
        files = FileSelection.WholeTree
        "synthetic".group {
            for ((index, role) in roles.withIndex()) {
                role.name.role {
                    if (index == 0 || !firstRoleOnly) constraint()
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
