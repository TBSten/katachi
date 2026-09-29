package com.example.kmp.processor

import com.example.kmp.groups.appGroup
import com.example.kmp.groups.dataGroup
import com.example.kmp.groups.featureGroup
import com.example.kmp.groups.gradleGroup
import com.example.kmp.groups.testingGroup
import com.example.kmp.groups.uiGroup
import com.example.kmp.projectArchitecture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.process

/**
 * A processor written outside `:katachi`, run against this sample's own definition.
 *
 * [PlatformOwnedFilesProcessor] and the [Owner] key it reads are both declared in this sample,
 * not in katachi. That is the whole point of this file: a user can add a processor of their
 * own on top of the one katachi ships (`assert()` / [me.tbsten.katachi.check.LayoutCheck]),
 * using nothing but the published processor API.
 *
 * The processor API is `@ExperimentalKatachiApi`, so this file opts in. That opt-in is the
 * wall doing its job: a real consumer of the published `katachi` artifact writes exactly this
 * to depend on a shape that is still moving, which is different from writing `owner = "..."`
 * in [com.example.kmp.groups.gradleGroup] -- that needs no opt-in, because the sugar hides it.
 */
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

                "Git" {
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

/**
 * Every file `owner = "platform"` reaches through the `"Gradle"` group -- tagged as a whole in
 * `groups/GradleGroup.kt` -- shared by both tests above so the file list is written once.
 * `tool/Git`'s `.gitignore` is deliberately not here: it is the one file that tells the two
 * tests apart.
 */
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
