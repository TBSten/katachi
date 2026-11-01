package com.example.sample

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File

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
