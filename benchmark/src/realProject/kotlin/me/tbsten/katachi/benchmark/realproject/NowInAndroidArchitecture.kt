package me.tbsten.katachi.benchmark.realproject

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gitTracked
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist

/**
 * A coarse katachi definition of android/nowinandroid, written to be measured, not to be right.
 *
 * It declares enough that the walk goes down every module and matches its files against a
 * realistic number of layout entries (feature `api` / `impl` modules, core modules, resources,
 * screenshots, the Gradle files), and puts a `konsist { }` on the Kotlin roles so that the run
 * with `FileConstraintCheck` parses them. Whatever it does not describe is reported as a
 * violation, which is fine: the number of violations is recorded next to the time, and a
 * change in it means the definition or the pinned commit changed, not katachi.
 *
 * The commit it was written against is pinned in the `bench-real-project` job of `.github/workflows/ci.yml`.
 */
internal fun nowInAndroidArchitecture(): Architecture = architecture {
    files = gitTracked()

    gradle(requireWrapper = false)

    "nowinandroid".group {
        "FeatureApi" {
            kotlinConstraint()
            layout {
                ":feature:*:api".module { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
            }
        }
        "FeatureImpl" {
            kotlinConstraint()
            layout {
                ":feature:*:impl".module { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
            }
        }
        "Core" {
            kotlinConstraint()
            layout {
                ":core:*".module { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
            }
        }
        "App" {
            kotlinConstraint()
            layout {
                ":app".module { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
                ":app-nia-catalog".module { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
                ":sync:*".module { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
                ":benchmarks".module { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
                ":lint".module { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
            }
        }
        "AndroidResource" {
            layout {
                ":**".module {
                    "src" / "*" / "res" / "**" / "*".file()
                    "src" / "*" / "AndroidManifest.xml".file()
                }
            }
        }
        "Screenshot" {
            layout {
                ":**".module { "src" / "*" / "screenshots" / "**" / "*.png".file() }
            }
        }
        "BuildLogic" {
            layout {
                "build-logic" / "**" / "*".file()
            }
        }
        "Document" {
            layout {
                "*.md".file()
                "docs" / "**" / "*".file()
            }
        }
    }
}

/**
 * A constraint every class meets, so that the run with `FileConstraintCheck` parses each file the
 * role owns and reports nothing: what is measured is the parse and the slice, not a report.
 */
private fun RoleScope.kotlinConstraint() {
    "classes have a name".konsist {
        classes().must { it.name.isNotEmpty() }
    }
}
