package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.waitFor
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The plugin's [me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge] as the new smokes (`GutterSurfacesSmokeTest`,
 * `GutterActionsSmokeTest`, `ArchModuleNameSmokeTest`) see it from Driver. [GutterBridgeRef] is the older half the existing
 * smokes keep using; this is the version with the doors the quality design (2.3) added.
 *
 * ```kotlin
 * val bridge = service(SmokeBridge::class, ideProject)
 * bridge.describeBanners(path)
 * ```
 */
@Remote("me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge", plugin = "me.tbsten.katachi.intellij")
interface SmokeBridge {
    fun describeState(): String

    fun describeGutter(path: String): String

    fun describeHighlights(path: String): String

    fun describeBanners(path: String): String

    fun describeDialog(): String

    fun describeRedraw(): String

    fun describeNoRole(): String

    fun clickGutter(path: String, line: Int): String

    fun chooseFromGutter(path: String, line: Int, row: String): String

    /** [chooseFromGutter] with the choosing queued on the EDT: the row opens a modal dialog, which would hold this call. */
    fun chooseFromGutterLater(path: String, line: Int, row: String): String

    fun applyFix(path: String, line: Int, prefix: String): String

    fun describeCaret(): String

    fun fillDialog(name: String, value: String): String

    fun pressDialog(button: String): String

    fun setGutterIcons(enabled: Boolean): String

    fun screenshotFrame(out: String): String

    fun screenshotGutterMenu(path: String, line: Int, out: String): String

    // ---- L-A (ide-authoring design 5): the doors for writing a definition ----

    /** The mistakes katachi found in the definition, the underlines and the banner of [path] (`AuthoringDebugReader.describeDefinitionErrors`). */
    fun describeDefinitionErrors(path: String): String

    /** What a completion at [offset] of [path] would offer (`AuthoringDebugReader.describeCompletion`). */
    fun describeCompletion(path: String, offset: Int): String

    /** The ◆, their menus and the Code Vision of the definition file [path] (`AuthoringDebugReader.describeDefinitionGutter`). */
    fun describeDefinitionGutter(path: String): String
}

/** What a failing wait adds to its message: everything the bridge can say about [path], so a red run says why the ◆ never came. */
internal fun SmokeBridge.diagnostics(path: String?): String = buildList {
    if (path != null) {
        add("gutter: " + attempt { describeGutter(path) })
        add("highlights: " + attempt { describeHighlights(path) })
        add("banners: " + attempt { describeBanners(path) })
    }
    add("redraw: " + attempt { describeRedraw() })
    add("dialog: " + attempt { describeDialog() })
}.joinToString("\n")

/** A line of the bridge's answer by its key, e.g. `valueOf("module")` of `describeState()`. */
internal fun String.valueOf(key: String): String? = lines().firstOrNull { it.startsWith("$key=") }?.removePrefix("$key=")

/** [waitFor] whose failure message is [diagnostics] (the ◆, the underlines, the banners, the last redraw and the dialog). */
internal fun Driver.waitForSmoke(
    what: String,
    bridge: SmokeBridge,
    path: String?,
    timeout: Duration,
    interval: Duration = 3.seconds,
    condition: () -> Boolean,
) {
    waitFor(what, timeout = timeout, interval = interval, errorMessage = { bridge.diagnostics(path) }, condition = condition)
}

/** The IDE window painted by the IDE itself (Starter's desktop screenshot is the wallpaper without screen-recording permission). */
internal fun SmokeBridge.shot(directory: Path, name: String) {
    println("screenshot " + screenshotFrame(directory.resolve("$name.png").toString()))
}

/**
 * Shots at 0, 2, 5 and 10 seconds after [path]'s ◆ is found. The daemon can draw the ◆ late, and whether it did is judged by eye
 * (the design does not search the pixels for it).
 */
internal fun SmokeBridge.shotsAfterGutter(directory: Path, name: String) {
    var passed = 0
    listOf(0, 2, 5, 10).forEach { second ->
        Thread.sleep((second - passed) * 1_000L)
        passed = second
        shot(directory, "$name-${second}s")
    }
}

private fun attempt(read: () -> String): String = runCatching(read).getOrElse { "unreadable: ${it.message}" }
