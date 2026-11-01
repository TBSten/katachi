package com.example

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File

class ProjectRootSpec : FreeSpec({
    "walking up from the working directory, the first gradlew found belongs to this sample" {
        val workingDirectory = File(System.getProperty("user.dir")).absoluteFile

        val root = generateSequence(workingDirectory) { it.parentFile }
            .firstOrNull { File(it, "gradlew").isFile }

        root.shouldNotBeNull()
        root.name shouldBe "jvm"
        root.parentFile.name shouldBe "sample"
    }

    "the tests run from the architecture-test module directory" {
        // The path used by the source-reading test in ProjectArchitectureSpec depends on
        // this. If the working directory ever moves, that test's lookup has to move too.
        File(System.getProperty("user.dir")).absoluteFile.name shouldBe "architecture-test"
    }
})
