package com.example.sample

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Sentinel for the project root lookup that katachi will do from step 2 on.
 *
 * katachi finds the project root by walking up from the working directory to the first
 * directory holding a `gradlew`. This test task runs in `sample/android/app`, so the walk
 * must stop at `sample/android` — the sample's own wrapper — and never reach the
 * repository root. If someone deletes `sample/android/gradlew`, or folds the sample into
 * the repository build, layout checks would silently start resolving paths against the
 * wrong root. This test fails first instead.
 */
class ProjectRootSpec : FreeSpec({
    "作業ディレクトリから親へ辿ったとき、最初に見つかる gradlew がこのサンプルのものである" {
        // `getProperty` is a platform type. The Android Gradle Plugin compiles unit tests in
        // strict mode, where passing it straight into File(String) is a warning, so the
        // contract is stated once here: the JVM always defines `user.dir`.
        val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(workingDir) { it.parentFile }
            .firstOrNull { File(it, "gradlew").isFile }

        withClue("working directory: $workingDir") {
            (root?.parentFile?.name to root?.name) shouldBe ("sample" to "android")
        }
    }
})
