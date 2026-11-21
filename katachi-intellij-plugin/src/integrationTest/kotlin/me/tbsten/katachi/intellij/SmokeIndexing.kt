package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.getModules
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.ide.IDETestContext
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * How long a smoke waits for the IDE to finish indexing the copy of sample/jvm before it starts.
 *
 * sample/jvm includes the whole repository, so the IDE indexes about 420 000 files (the root build's libraries, the JDKs and the
 * build scripts' classpath): under 4 minutes on an Apple silicon laptop and about 14 on a GitHub macOS runner, which a
 * 15-minute wait did not cover. A run whose index is done earlier does not wait for the rest.
 *
 * ```kotlin
 * context.runIdeWithDriver(runTimeout = SMOKE_RUN_TIMEOUT).useDriverAndCloseIde {
 *     waitForIndicators(singleProject(), SMOKE_INDEXING_TIMEOUT)
 * }
 * ```
 */
internal val SMOKE_INDEXING_TIMEOUT: Duration = 40.minutes

/** The whole IDE run of one smoke: [SMOKE_INDEXING_TIMEOUT] plus the smoke's own steps. */
internal val SMOKE_RUN_TIMEOUT: Duration = 75.minutes

/**
 * Waits until the Gradle import of the opened copy has added its modules and the IDE has indexed them.
 *
 * `waitForIndicators` alone can return before the import starts: on a GitHub macOS runner with IDEA 2026.3 the status bar was
 * empty for more than its 10 quiet seconds between the project's opening and the import, and the smoke's first `openFile` did not
 * find a file of a module the import had not added yet.
 *
 * The first import can also fail at once and leave nothing to wait for (KATACHI-444): on a GitHub macOS runner Android Studio ran it
 * 28 seconds after starting, before its project JDK was registered, and the import failed in 0.5 s with "Invalid Gradle JDK
 * configuration found". Nothing started it again, so the wait ran its whole [SMOKE_INDEXING_TIMEOUT] and the smoke failed an hour
 * later. So when no module has come after [SMOKE_IMPORT_RETRY_AFTER], the import is asked for again ([resyncGradle]), at most
 * [SMOKE_IMPORT_MAX_RETRIES] times, and only after ten minutes, which a healthy first import (about 3 minutes on IDEA 2026.3) does not reach.
 *
 * ```kotlin
 * context.runIdeWithDriver(runTimeout = SMOKE_RUN_TIMEOUT).useDriverAndCloseIde {
 *     waitForProjectOpen(5.minutes)
 *     waitForImportAndIndex(singleProject())
 * }
 * ```
 */
internal fun Driver.waitForImportAndIndex(project: Project) {
    var retries = 0
    var lastAsk = TimeSource.Monotonic.markNow()
    var lastAnswer = "not asked"
    // Before the import the project has at most the one module of its directory; sample/jvm's import adds a module per source set.
    waitFor(
        "the Gradle import of the copy",
        timeout = SMOKE_INDEXING_TIMEOUT,
        interval = 5.seconds,
        errorMessage = { "no module after $retries re-sync(s); the last one answered: $lastAnswer" },
    ) {
        if (getModules(project).size > 1) {
            true
        } else {
            if (retries < SMOKE_IMPORT_MAX_RETRIES && lastAsk.elapsedNow() >= SMOKE_IMPORT_RETRY_AFTER) {
                retries++
                lastAsk = TimeSource.Monotonic.markNow()
                lastAnswer = resyncGradle(project)
                println("no module of the copy yet; asked the IDE to import it again (#$retries): $lastAnswer")
            }
            false
        }
    }
    waitForIndicators(project, SMOKE_INDEXING_TIMEOUT)
}

/** How long the first import may leave no module before [waitForImportAndIndex] asks for it again, and again after each ask. */
internal val SMOKE_IMPORT_RETRY_AFTER: Duration = 10.minutes

/** How many times [waitForImportAndIndex] asks for the import again; the 40 minutes hold the first import and these. */
internal const val SMOKE_IMPORT_MAX_RETRIES: Int = 2

/**
 * Asks the IDE to import the Gradle project again, through the debug bridge's `performAction` (the action of the project, not of the
 * focused window). Android Studio's own "Sync Project with Gradle Files" first (it runs the sync Android Studio's import needs), then
 * the platform's "Reload All Gradle Projects"; the answer is the action that ran, or why none did (the bridge is off, no such action).
 */
internal fun Driver.resyncGradle(project: Project): String = runCatching {
    val bridge = service(SmokeBridge::class, project)
    listOf("Android.SyncProject", "ExternalSystem.RefreshAllProjects")
        .asSequence()
        .map { bridge.performAction(it) }
        .firstOrNull { it != "performed=none" } ?: "performed=none"
}.getOrElse { "failed: ${it::class.simpleName}: ${it.message}" }

/**
 * Keeps the Gradle import from downloading the libraries' source jars, which add about 77 000 files to the index and nothing a smoke
 * reads. Call it before the IDE starts: the setting is read from the config directory at startup.
 *
 * ```kotlin
 * Starter.newContext(name, testCase, false).skipLibrarySources()
 * ```
 */
internal fun IDETestContext.skipLibrarySources(): IDETestContext = apply {
    val options = paths.configDir.resolve("options").also { Files.createDirectories(it) }
    options.resolve("advancedSettings.xml").writeText(
        """
        <application>
          <component name="AdvancedSettings">
            <option name="settings">
              <map>
                <entry key="gradle.download.sources" value="false" />
              </map>
            </option>
          </component>
        </application>
        """.trimIndent(),
    )
}
