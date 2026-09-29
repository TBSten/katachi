package com.example.kmp

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Sentinel for the assumption katachi's project root detection relies on in this sample:
 * walking up from the working directory, the first `gradlew` belongs to this sample, not to
 * the katachi repository that contains it.
 *
 * This test runs inside `:architecture-test`, i.e. one directory below `sample/kmp`, so the
 * walk has to survive at least one level. If someone ever deletes `sample/kmp/gradlew` and
 * relies on the repository wrapper, this fails instead of the layout checks silently
 * scanning the whole repository.
 */
class ProjectRootSpec : FreeSpec({
    "walking up from the working directory, the first gradlew found belongs to this sample" {
        // `getProperty` is a platform type, so the contract is stated once here: the JVM
        // always defines `user.dir`.
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
