package me.tbsten.katachi.intellij

import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.singleProject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What the ◆ and the annotations lead to (ide-quality design 2.2): the check run from the ◆'s menu, the quick fix "Go to the
 * rule" opening `ServiceRole.kt` at the rule, a file-level banner over a file that lacks a declaration its role requires, and the
 * generate dialog opened from the ◆'s menu, filled, generated and cancelled, as a user does it.
 *
 * Gradle runs `:architecture-test:test` and `katachiTemplate` inside the IDE, so this is the slow one of the three smokes; it is
 * kept apart from [GutterSurfacesSmokeTest] so that a failing check does not hide whether the ◆ is drawn.
 * Written, compiled and not run by the build (`./gradlew integrationTest --tests '*GutterActionsSmokeTest*'` opens an IDE; run
 * by the verification lane, V4).
 *
 * Not planted on purpose: a WARNING in the file that has the banner. The platform drops a WARNING underline when an ERROR banner
 * is up, which is the platform and not the plugin, so it is kept out of this copy.
 */
class GutterActionsSmokeTest {
    private val ide = SmokeIde("gutter-actions")

    @Test
    fun `検査 quick fix banner 生成ダイアログの生成とキャンセルが利用者の操作の列のとおりに動く`() {
        // `internal` is rejected by the "Must be public" constraint; the second file lacks what the role's ktFileContent requires.
        val project = ide.prepareCopy { copy ->
            copy.resolve(PROBE).writeText("package com.example.service\n\ninternal class ProbeService\n")
            copy.resolve(BANNER).writeText("package com.example.service\n")
        }
        val probe = project.resolve(PROBE).toString()
        val banner = project.resolve(BANNER).toString()
        val health = project.resolve(SERVICE).toString()

        ide.run(project) { ideProject, bridge ->
            // 1. Run the check from the ◆ of the planted file; the underline comes when the check has run.
            openFile(PROBE, waitForCodeAnalysis = false)
            waitForSmoke("the ◆ on ProbeService", bridge, probe, timeout = 15.minutes) { "katachi: domain.Service" in bridge.describeGutter(probe) }
            val chosen = bridge.chooseFromGutter(probe, 3, "▶ Run the check")
            assertTrue(chosen.startsWith("chosen=▶ Run the check (:") && chosen.endsWith(":test)"), chosen)
            waitForSmoke("the underline under ProbeService", bridge, probe, timeout = 20.minutes, interval = 5.seconds) {
                "rejected ProbeService" in bridge.describeHighlights(probe)
            }
            // The widget at the top right can stay at the warnings' count with an ERROR drawn: judged by eye, not asserted.
            bridge.shot(ide.screenshots, "1-probe-underline")

            // 2. The quick fix: both fixes are listed, "Go to the rule" opens the rule's file at the rule's line.
            val highlight = bridge.describeHighlights(probe).lines().first { it.startsWith("highlight=ERROR|3:") }
            assertTrue("Go to the rule (ServiceRole.kt:94)" in highlight && "Run the check again" in highlight, highlight)
            assertEquals("applied=Go to the rule (ServiceRole.kt:94)", bridge.applyFix(probe, 3, "Go to the rule"))
            waitForSmoke("ServiceRole.kt open at line 94", bridge, probe, timeout = 1.minutes, interval = 1.seconds) {
                runCatching { bridge.describeCaret() }.getOrDefault("") == "caret=ServiceRole.kt|94"
            }
            bridge.shot(ide.screenshots, "2-go-to-the-rule")

            // 3. The banner: a file-level one, with the two links of the quick fixes.
            openFile(BANNER, waitForCodeAnalysis = false)
            waitForSmoke("the banner over BannerProbeService", bridge, banner, timeout = 20.minutes, interval = 5.seconds) {
                "BannerProbeService" in bridge.describeBanners(banner)
            }
            val banners = bridge.describeBanners(banner).lines().filter { it.startsWith("banner=") }
            assertEquals(1, banners.size, banners.toString())
            assertTrue("Go to the rule" in banners.single() && "Run the check again" in banners.single(), banners.toString())
            bridge.shot(ide.screenshots, "3-banner")

            // 4. The generate dialog, from the menu of a ◆ on a file the user is looking at.
            openFile(SERVICE, waitForCodeAnalysis = false)
            waitForSmoke("the ◆ on HealthService", bridge, health, timeout = 5.minutes) { "katachi: domain.Service" in bridge.describeGutter(health) }
            val serviceLine = markLine(bridge.describeGutter(health))
            assertTrue(bridge.chooseFromGutterLater(health, serviceLine, "+ New domain.Service").startsWith("chosen=+ New domain.Service"))
            waitForSmoke("the generate dialog", bridge, health, timeout = 1.minutes, interval = 1.seconds) { bridge.describeDialog().valueOf("dialog") == "open" }
            // TODO(smoke-helper): the specifier of a template with no id is whatever the definition says; fixed at the first V4 run.
            assertTrue(bridge.describeDialog().valueOf("template").orEmpty().startsWith("domain.Service"), bridge.describeDialog())
            assertEquals("filled=name", bridge.fillDialog("name", "Smoke"))
            waitForSmoke("the target path for Smoke", bridge, health, timeout = 30.seconds, interval = 1.seconds) {
                bridge.describeDialog().let { it.valueOf("okEnabled") == "true" && it.valueOf("target").orEmpty().endsWith("SmokeService.kt") }
            }
            bridge.shot(ide.screenshots, "4-dialog-filled")
            assertEquals("pressed=generate", bridge.pressDialog("generate"))
            val generated = project.resolve(GENERATED)
            waitForSmoke("SmokeService.kt written", bridge, health, timeout = 5.minutes, interval = 3.seconds) { generated.exists() }
            assertEquals("none", bridge.describeDialog().valueOf("dialog"))
            assertTrue("class SmokeService" in generated.readText(), generated.readText())
            waitForSmoke("SmokeService.kt open with its ◆", bridge, generated.toString(), timeout = 5.minutes) {
                // Written after the definition was read, so it may say (stale); both are the role's name first.
                bridge.describeGutter(generated.toString()).lines().any { it.startsWith("mark=") && "katachi: domain.Service" in it }
            }
            bridge.shot(ide.screenshots, "5-generated")

            // 5. Cancel: the same steps again, then nothing is added.
            openFile(SERVICE, waitForCodeAnalysis = false)
            val before = ide.sourcesOf(project)
            assertTrue(bridge.chooseFromGutterLater(health, serviceLine, "+ New domain.Service").startsWith("chosen="))
            waitForSmoke("the generate dialog again", bridge, health, timeout = 1.minutes, interval = 1.seconds) { bridge.describeDialog().valueOf("dialog") == "open" }
            assertEquals("pressed=cancel", bridge.pressDialog("cancel"))
            waitForSmoke("the dialog closed", bridge, health, timeout = 30.seconds, interval = 1.seconds) { bridge.describeDialog().valueOf("dialog") == "none" }
            assertFalse(ide.sourcesOf(project) != before, "cancel added or removed a file")
            bridge.shot(ide.screenshots, "6-cancelled")
        }
    }

    private fun markLine(gutter: String): Int = gutter.lines().first { it.startsWith("mark=") }.removePrefix("mark=").substringBefore('|').toInt()

    private companion object {
        const val SERVICE_DIR = "src/main/kotlin/com/example/service"
        const val SERVICE = "$SERVICE_DIR/HealthService.kt"
        const val PROBE = "$SERVICE_DIR/ProbeService.kt"
        const val BANNER = "$SERVICE_DIR/BannerProbeService.kt"
        const val GENERATED = "$SERVICE_DIR/SmokeService.kt"
    }
}
