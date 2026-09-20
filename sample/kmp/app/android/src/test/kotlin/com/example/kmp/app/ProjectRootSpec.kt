package com.example.kmp.app

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Sentinel for the assumption katachi's project root detection (implementation step 2) will
 * rely on: walking up from the working directory, the first `gradlew` belongs to this
 * sample, not to the katachi repository that contains it.
 *
 * This test runs inside `:app:android`, i.e. two directories below `sample/kmp`, so it also
 * proves the walk survives a nested module path. If someone ever deletes `sample/kmp/gradlew`
 * and relies on the repository wrapper, this fails instead of the step 2 checks silently
 * scanning the whole repository.
 */
class ProjectRootSpec : FreeSpec({
    "作業ディレクトリから親へ辿ったとき、最初に見つかる gradlew がこのサンプルのものである" {
        // `getProperty` is a platform type. The Android Gradle Plugin compiles unit tests in
        // strict mode, where passing it straight into File(String) is a warning, so the
        // contract is stated once here: the JVM always defines `user.dir`.
        val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(workingDir) { it.parentFile }
            .firstOrNull { File(it, "gradlew").isFile }

        root shouldBe workingDir.resolveSampleRoot()
        root?.name shouldBe "kmp"
        root?.parentFile?.name shouldBe "sample"
    }
})

/**
 * The directory this sample lives in, found without looking for `gradlew`, so that the test
 * above compares two independently derived answers.
 */
private fun File.resolveSampleRoot(): File =
    generateSequence(this) { it.parentFile }
        .first { it.name == "kmp" && it.parentFile?.name == "sample" }
