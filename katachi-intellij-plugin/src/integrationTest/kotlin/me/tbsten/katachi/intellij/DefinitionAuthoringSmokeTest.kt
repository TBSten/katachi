package me.tbsten.katachi.intellij

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Writing a definition in a real IDE (ide-authoring plan 2.6, E-1): a mistake in `RepositoryRole.kt` is underlined and announced and then
 * goes when it is mended, the names `captureValue("…")` offers are the capture names of the template, and the ◆ and the Code Vision
 * of `ServiceRole.kt` say how many files the role and its layout line hold.
 *
 * The file is edited on the disk and the IDE is told to look again (`Synchronize`), as a person saving from another program does; the
 * completion is asked of the bridge for the place after the quote, so no key is typed. Written, compiled and not run by the build
 * (`./gradlew integrationTest --tests '*DefinitionAuthoringSmokeTest*'` opens an IDE; run by the verification lane, V4, on 2026.1
 * and, in the nightly job, 2026.3). Judged by eye: the screenshots of the underline, the banner and the Code Vision.
 */
class DefinitionAuthoringSmokeTest {
    private val ide = SmokeIde("definition-authoring")

    @Test
    fun `定義の誤りが下線と通知で出て直すと消え captureValueの候補と役割の◆とCodeVisionが出る`() {
        val project = ide.prepareCopy()
        val repository = project.resolve(REPOSITORY)
        val service = project.resolve(SERVICE)
        val original = repository.readText()
        val serviceText = service.readText()
        // The line the mistake is written on (1-based): where the error's site and the underline must both be.
        val brokenLine = original.lines().indexOfFirst { LAYOUT_LINE in it } + 1
        assertTrue(brokenLine > 0, "$LAYOUT_LINE is not in RepositoryRole.kt")
        // `.template { }` needs its import and the opt-in; both go on lines the file already has, so the mistake's line number stays.
        val brokenText = original.replace(LAYOUT_LINE, BROKEN_LINE)
            .replaceFirst(REPOSITORY_PACKAGE, "@file:OptIn(me.tbsten.katachi.ExperimentalKatachiApi::class) $REPOSITORY_PACKAGE")
            .replaceFirst("import me.tbsten.katachi.dsl.kotlin.ktFile", "import me.tbsten.katachi.dsl.kotlin.ktFile; import me.tbsten.katachi.dsl.template")
        // replaceFirst changes nothing when the text is gone; a mistake without its opt-in or import would fail for another reason.
        assertTrue("@file:OptIn" in brokenText && "import me.tbsten.katachi.dsl.template" in brokenText, "RepositoryRole.kt no longer has the package line or the ktFile import the mistake is stitched onto")
        val quote = serviceText.indexOf(CAPTURE_VALUE) + CAPTURE_VALUE.length

        ide.run(project) { _, bridge ->
            val repositoryPath = repository.toString()
            val servicePath = service.toString()

            // 1. A definition that reads: no mistake, and the layout line of ServiceRole.kt has its count.
            openFile(SERVICE, waitForCodeAnalysis = false)
            waitForSmoke("the ◆ and the Code Vision of ServiceRole.kt", bridge, servicePath, timeout = 15.minutes) {
                bridge.describeDefinitionGutter(servicePath).let { "katachi: Domain.Service" in it && it.lines().any { line -> line.startsWith("lens=") } }
            }
            val gutter = bridge.describeDefinitionGutter(servicePath)
            assertTrue(gutter.lines().any { it.startsWith("mark=") && "katachi: Domain.Service — " in it }, gutter)
            assertTrue(gutter.lines().any { it.startsWith("lens=") && (it.endsWith(" file") || it.endsWith(" files")) }, gutter)
            assertEquals("0", bridge.describeDefinitionErrors(servicePath).valueOf("errors"), bridge.describeDefinitionErrors(servicePath))
            bridge.shot(ide.screenshots, "1-service-gutter")

            // 2. The names captureValue("…") offers: the capture of the template, with the place read from the PSI.
            val completion = bridge.describeCompletion(servicePath, quote)
            assertTrue(completion.valueOf("site").orEmpty().startsWith("CaptureValue("), completion)
            assertTrue(completion.lines().any { it.startsWith("candidate=name|") }, completion)

            // 3. A name-less `*` with a `.template { "" }`: katachi cannot read the definition. Underlined where it was written, announced in a banner.
            openFile(REPOSITORY, waitForCodeAnalysis = false)
            repository.writeText(brokenText)
            invokeAction("Synchronize")
            val underlined = "highlight=ERROR|$brokenLine:"
            waitForSmoke("the mistake of RepositoryRole.kt underlined on line $brokenLine and announced", bridge, repositoryPath, timeout = 2.minutes, interval = 3.seconds) {
                bridge.describeDefinitionErrors(repositoryPath).lines().let { lines ->
                    lines.any { it.startsWith(underlined) } && lines.any { it.startsWith(BANNER) }
                }
            }
            val broken = bridge.describeDefinitionErrors(repositoryPath)
            assertTrue(broken.valueOf("errors")?.toIntOrNull()?.let { it > 0 } == true, broken)
            assertTrue(broken.lines().any { it.startsWith("error=") && "|RepositoryRole.kt:$brokenLine|" in it }, broken)
            assertTrue(broken.lines().any { it.startsWith(underlined) }, broken)
            assertTrue(broken.lines().any { it.startsWith(BANNER) }, broken)
            bridge.shot(ide.screenshots, "3-mistake")

            // 4. Mended: the underline and the banner go.
            repository.writeText(original)
            invokeAction("Synchronize")
            waitForSmoke("the mistake gone (no error, no underline, no banner)", bridge, repositoryPath, timeout = 2.minutes, interval = 3.seconds) {
                bridge.describeDefinitionErrors(repositoryPath).let { it.valueOf("errors") == "0" && it.valueOf("highlights") == "0" && it.valueOf("banners") == "0" }
            }
            assertEquals("0", bridge.describeDefinitionErrors(repositoryPath).valueOf("highlights"))
            bridge.shot(ide.screenshots, "4-mended")

            // 5. The ◆ of a role lists the files it holds, as its menu.
            val menu = bridge.describeDefinitionGutter(servicePath)
            assertTrue(menu.lines().any { it.startsWith("item=") && "Service.kt" in it }, menu)
        }
    }

    private companion object {
        const val EXAMPLE = "architecture-test/src/test/kotlin/com/example"
        const val REPOSITORY = "$EXAMPLE/data/RepositoryRole.kt"
        const val SERVICE = "$EXAMPLE/domain/ServiceRole.kt"
        const val REPOSITORY_PACKAGE = "package com.example.data"
        const val BANNER = "banner=katachi could not read the definition"
        const val CAPTURE_VALUE = "captureValue(\""
        const val LAYOUT_LINE = "mainSourceSet / kotlin / \"com/example/repository\" / \"*Repository\".ktFile()"
        const val BROKEN_LINE = "mainSourceSet / kotlin / \"com/example/repository\" / \"*\".ktFile().template { \"\" }"
    }
}
