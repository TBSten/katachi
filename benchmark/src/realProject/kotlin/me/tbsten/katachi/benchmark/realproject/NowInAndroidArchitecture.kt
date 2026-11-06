package me.tbsten.katachi.benchmark.realproject

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gitTracked
import me.tbsten.katachi.dsl.gradle.gradle
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
        "FeatureApi".role {
            kotlinConstraint()
            layout {
                "feature/*/api" { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
            }
        }
        "FeatureImpl".role {
            kotlinConstraint()
            layout {
                "feature/*/impl" { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
            }
        }
        "Core".role {
            kotlinConstraint()
            layout {
                "core/*" { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
            }
        }
        "App".role {
            kotlinConstraint()
            layout {
                "app" { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
                "app-nia-catalog" { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
                "sync/*" { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
                "benchmarks" { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
                "lint" { "src" / "*" / "kotlin" / "**" / "*".ktFile() }
            }
        }
        "AndroidResource".role {
            layout {
                "**" {
                    "src" / "*" / "res" / "**" / "*".file()
                    "src" / "*" / "AndroidManifest.xml".file()
                }
            }
        }
        "Screenshot".role {
            layout {
                "**" { "src" / "*" / "screenshots" / "**" / "*.png".file() }
            }
        }
        "BuildLogic".role {
            layout {
                "build-logic" / "**" / "*".file()
            }
        }
        "Document".role {
            layout {
                "*.md".file()
                "docs" / "**" / "*".file()
            }
        }
        // nowinandroid tracks part of `.idea/` (`.idea/*` with `!/.idea/codeStyles/...`), so
        // without this the count would move by one. Ignored, not declared: coarse is the point.
        "IdeSettings".role {
            layout {
                ".idea".ignore()
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
