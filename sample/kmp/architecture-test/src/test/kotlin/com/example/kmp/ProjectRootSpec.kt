package com.example.kmp

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File

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

private fun File.resolveSampleRoot(): File =
    generateSequence(this) { it.parentFile }
        .first { it.name == "kmp" && it.parentFile?.name == "sample" }
