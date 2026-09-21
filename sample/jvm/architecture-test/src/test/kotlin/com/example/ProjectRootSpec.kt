package com.example

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Sentinel for the project-root detection that lands in step 2.
 *
 * katachi will locate the project root by walking up from the working directory to the
 * first `gradlew`. The tests now run from `sample/jvm/architecture-test`, one level deeper
 * than before, so the walk passes through a module directory first - which is exactly the
 * normal case for a multi-module project, and must still land on `sample/jvm`.
 *
 * That only works because `sample/jvm` carries its own wrapper. If someone ever deletes it
 * to "clean up duplication", the walk would run past `sample/` and hit the katachi
 * repository root, and every path in the layout would be wrong. This test fails first, and
 * says why.
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

    "テストは architecture-test モジュールのディレクトリから実行される" {
        // The path used by the source-reading test in ProjectArchitectureSpec depends on
        // this. If the working directory ever moves, that test's lookup has to move too.
        File(System.getProperty("user.dir")).absoluteFile.name shouldBe "architecture-test"
    }
})
