package com.example.kmp.processors

import com.example.kmp.app.appGroup
import com.example.kmp.data.dataGroup
import com.example.kmp.feature.featureGroup
import com.example.kmp.gradle.gradleGroup
import com.example.kmp.projectArchitecture
import com.example.kmp.testing.testingGroup
import com.example.kmp.ui.uiGroup
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.process

@OptIn(ExperimentalKatachiApi::class)
class PlatformOwnedFilesSpec : FreeSpec({
    "collects every file of the roles tagged owner = \"platform\"" {
        val files = projectArchitecture.process(PlatformOwnedFilesProcessor(owner = "platform")).getOrThrow()

        files shouldContainExactlyInAnyOrder (platformOwnedFilesWithoutGit + ".gitignore")
    }

    "no files are collected from a role without the owner tag (guards against a vacuous pass)" {
        // A definition rebuilt with a role that has the same layout as tool/Git but no owner. The only
        // difference from the real toolGroup() is this one tag, so if the processor returned every file
        // without actually reading Owner, .gitignore would show up here too.
        //
        // This one is copied by hand instead of calling git(): calling it would add the owner,
        // and the point of this test (the result changes with the tag) would be lost.
        val architectureWithUntaggedGit = architecture {
            featureGroup()
            uiGroup()
            dataGroup()
            testingGroup()
            appGroup()
            gradleGroup()
            "tool".group {
                documented = false
                title = "Tool"

                "Git".role {
                    summary = ".gitignore and the like"
                    documented = false
                    example(".gitignore", "Keeps generated files out of Git")
                    layout {
                        ".gitignore".file()
                    }
                    // owner is deliberately left unset.
                }
            }
        }

        val files = architectureWithUntaggedGit.process(PlatformOwnedFilesProcessor(owner = "platform"))
            .getOrThrow()

        files shouldContainExactlyInAnyOrder platformOwnedFilesWithoutGit
    }
})

private val platformOwnedFilesWithoutGit = listOf(
    "app/android/build.gradle.kts",
    "architecture-test/build.gradle.kts",
    "data/build.gradle.kts",
    "feature/home/build.gradle.kts",
    "feature/settings/build.gradle.kts",
    "navigation/build.gradle.kts",
    "testing/build.gradle.kts",
    "ui/build.gradle.kts",
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "gradle/sample.versions.toml",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
)
