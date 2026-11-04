package me.tbsten.katachi.intellij

import com.intellij.ide.starter.ide.IDETestContext
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
