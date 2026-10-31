package me.tbsten.katachi.intellij

import com.intellij.driver.sdk.openFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * The definition module is found by its Gradle path, not by the name `:architecture-test` (ide-quality design 2.2): the copy's
 * settings include it as `:arch` (the directory keeps its name), and the ◆ still says what allowed `class HealthService`, the
 * check's row names `:arch:test`, the state reports `module=:arch`, and a file no role holds still gets its notification.
 *
 * Written, compiled and not run by the build (`./gradlew integrationTest --tests '*ArchModuleNameSmokeTest*'` opens an IDE; run
 * by the verification lane, V4).
 */
class ArchModuleNameSmokeTest {
    private val ide = SmokeIde("arch-module-name")

    @Test
    fun `定義のモジュールが_archでも◆の許可元と検査の行とmoduleと当たらないファイルの通知が変わらない`() {
        val project = ide.prepareCopy { copy ->
            val settings = copy.resolve("settings.gradle.kts")
            val text = settings.readText()
            check("include(\":architecture-test\")" in text) { "sample/jvm's settings no longer include :architecture-test; update this smoke" }
            settings.writeText(
                text.replace(
                    "include(\":architecture-test\")",
                    "include(\":arch\")\nproject(\":arch\").projectDir = file(\"architecture-test\")",
                ),
            )
        }
        // After the commit, so the file is untracked: a file no role holds, under a directory no role knows (the case of NoRoleSmokeTest).
        project.resolve(STRAY).also { it.parent.toFile().mkdirs() }.writeText("package com.example.service.helpers\n\nclass NoRoleProbe\n")
        val service = project.resolve(SERVICE).toString()
        val stray = project.resolve(STRAY).toString()
        val allowedBy = ServiceRoleFacts.of(project).let { "allowed by ${it.allowedDeclaration} (ServiceRole.kt:${it.allowedLine})" }

        ide.run(project) { _, bridge ->
            // 1. The ◆ names the line of the rule exactly as with :architecture-test.
            openFile(SERVICE, waitForCodeAnalysis = false)
            waitForSmoke("the ◆ on HealthService", bridge, service, timeout = 15.minutes) { allowedBy in bridge.describeGutter(service) }
            bridge.shot(ide.screenshots, "1-service-gutter")

            // 2. The check's row in the menu runs the module under its real path.
            val line = bridge.describeGutter(service).lines().first { it.startsWith("mark=") }.removePrefix("mark=").substringBefore('|').toInt()
            val menu = bridge.clickGutter(service, line)
            assertTrue("▶ Run the check (:arch:test)" in menu, menu)

            // 3. The state, without the tool window ever opened.
            assertEquals(":arch", bridge.describeState().valueOf("module"), bridge.diagnostics(service))

            // 4. A file no role holds still gets the notification (its detection does not depend on the module's name either).
            openFile(STRAY, waitForCodeAnalysis = false)
            waitForSmoke("the no-role notification", bridge, stray, timeout = 5.minutes) { bridge.describeNoRole().valueOf("noRole") == "shown" }
            bridge.shot(ide.screenshots, "4-no-role")
        }
    }

    private companion object {
        const val SERVICE = "src/main/kotlin/com/example/service/HealthService.kt"
        const val STRAY = "src/main/kotlin/com/example/service/helpers/NoRoleProbe.kt"
    }
}
