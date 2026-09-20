package com.example

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Sentinel for the project-root detection that lands in step 2.
 *
 * katachi will locate the project root by walking up from the working directory to the
 * first `gradlew`. That only works for this sample because `sample/jvm` carries its own
 * wrapper. If someone ever deletes it to "clean up duplication", the walk would run past
 * `sample/` and hit the katachi repository root, and every path in the layout would be
 * wrong. This test fails first, and says why.
 */
class ProjectRootSpec : FreeSpec({
    "作業ディレクトリから親へ辿ったとき、最初に見つかる gradlew がこのサンプルのものである" {
        val workingDirectory = File(System.getProperty("user.dir")).absoluteFile

        val root = generateSequence(workingDirectory) { it.parentFile }
            .firstOrNull { File(it, "gradlew").isFile }

        root.shouldNotBeNull()
        root.name shouldBe "jvm"
        root.parentFile.name shouldBe "sample"
    }
})
