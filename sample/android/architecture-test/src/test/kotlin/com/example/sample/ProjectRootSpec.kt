package com.example.sample

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Sentinel for the assumption katachi's project root lookup relies on in this sample.
 *
 * katachi finds the project root by walking up from the working directory to the first
 * directory holding a build tool's wrapper (the Gradle one here) or git's own files. This
 * test task runs in `sample/android/architecture-test`, so the walk must stop at
 * `sample/android` — the sample's own wrapper — and never reach the repository root. If
 * someone deletes `sample/android/gradlew`, or folds the sample into the repository build,
 * layout checks would silently start resolving paths against the wrong root. This test fails
 * first instead.
 */
class ProjectRootSpec : FreeSpec({
    "the first gradlew found walking up from the working directory is this sample's own" {
        // `getProperty` is a platform type, so the contract is stated once here: the JVM
        // always defines `user.dir`.
        val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(workingDir) { it.parentFile }
            .firstOrNull { File(it, "gradlew").isFile }

        withClue("working directory: $workingDir") {
            (root?.parentFile?.name to root?.name) shouldBe ("sample" to "android")
        }
    }
})
