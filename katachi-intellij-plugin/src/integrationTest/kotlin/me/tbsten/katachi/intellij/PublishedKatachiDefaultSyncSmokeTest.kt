package me.tbsten.katachi.intellij

import com.intellij.driver.sdk.openFile
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * KATACHI-363 on a real Android Studio left at its defaults: its Gradle sync builds no task list, so the plugin cannot find the
 * definition module by its tasks and falls back to the modules that depend on the katachi library. The other smokes cannot see
 * this: sample/jvm takes katachi from the repository's included build (no library dependency), so they turn the task list on
 * ([SmokeProduct.prepare]). Here katachi is published from this working tree into a Maven repository under the work directory,
 * and the copy of sample/jvm takes it by its coordinates, as a user's project does; the ◆ on `class HealthService` then shows
 * the fallback found `:architecture-test`.
 *
 * Android Studio only (`./gradlew integrationTestAndroidStudio --tests '*PublishedKatachiDefaultSyncSmokeTest*'`, nightly and
 * prerelease 10-5): IDEA always builds the task list. Publishing runs the katachi build first, which adds minutes.
 */
class PublishedKatachiDefaultSyncSmokeTest {
    private val ide = SmokeIde("published-default-sync")

    @Test
    fun `既定設定のAndroidStudioで公開座標のkatachiに依存する定義モジュールが見つかり◆が出る`() {
        assumeTrue(SmokeProduct.androidStudio, "IDEA always builds the task list; the fallback is Android Studio's case")
        val repository = Paths.get(System.getProperty("katachi.smoke.sampleProject")).resolve("../..").normalize()
        val version = katachiVersion(repository)
        val maven = ide.workDir.resolve("maven-repository")
        publishKatachi(repository, maven)

        val project = ide.prepareCopy { copy -> takeKatachiByCoordinates(copy.resolve("settings.gradle.kts"), maven, version) }
        val service = project.resolve(SERVICE).toString()
        val allowedBy = ServiceRoleFacts.of(project).let { "allowed by ${it.allowedDeclaration} (ServiceRole.kt:${it.allowedLine})" }

        ide.run(project, buildTaskList = false) { _, bridge ->
            openFile(SERVICE, waitForCodeAnalysis = false)
            waitForSmoke("the ◆ on HealthService", bridge, service, timeout = 15.minutes) { allowedBy in bridge.describeGutter(service) }
            bridge.shot(ide.screenshots, "1-service-gutter")
        }
    }

    /** The version this working tree publishes: the root catalog's `katachi`, which `:katachi` and the Gradle plugin take. */
    private fun katachiVersion(repository: Path): String =
        repository.resolve("gradle/libs.versions.toml").readLines()
            .firstNotNullOfOrNull { VERSION.matchEntire(it.trim())?.groupValues?.get(1) }
            ?: error("gradle/libs.versions.toml has no `katachi = \"<version>\"`; update PublishedKatachiDefaultSyncSmokeTest")

    /**
     * `publishToMavenLocal` of the katachi build, into [maven] instead of `~/.m2` (`maven.repo.local`), unsigned: the library,
     * konsist, konture and the Gradle plugin with its marker.
     */
    private fun publishKatachi(repository: Path, maven: Path) {
        val command = listOf(
            repository.resolve("gradlew").toString(), "publishToMavenLocal", "-Pkatachi.skipSigning", "-Dmaven.repo.local=$maven",
            "--no-daemon", "--console=plain",
        )
        val process = ProcessBuilder(command).directory(repository.toFile()).inheritIO().start()
        if (!process.waitFor(PUBLISH_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            error("Timed out after $PUBLISH_TIMEOUT_MINUTES minutes: ${command.joinToString(" ")}")
        }
        check(process.exitValue() == 0) { "Failed: ${command.joinToString(" ")}" }
    }

    /**
     * Turns the copy's composite build into a user's setup: no `includeBuild("../..")` (it is there twice, for the plugin and for the
     * library), [maven] first in both repository lists, and the plugin's version given where a user gives it.
     */
    private fun takeKatachiByCoordinates(settings: Path, maven: Path, version: String) {
        val text = settings.readText()
        val includes = Regex("""(?m)^\s*includeBuild\("\.\./\.\."\)\s*\n""")
        check(includes.findAll(text).count() == 2) { "sample/jvm's settings no longer include the repository twice; update this smoke" }
        check(text.split("repositories {\n").size == 3) { "sample/jvm's settings no longer have two repository lists; update this smoke" }
        check("pluginManagement {\n" in text) { "sample/jvm's settings no longer start with pluginManagement; update this smoke" }
        settings.writeText(
            text.replace(includes, "")
                .replace("repositories {\n", "repositories {\n        maven { url = uri(\"${maven.toUri()}\") }\n")
                .replace("pluginManagement {\n", "pluginManagement {\n    plugins { id(\"me.tbsten.katachi\") version \"$version\" }\n"),
        )
    }

    private companion object {
        const val SERVICE = "src/main/kotlin/com/example/service/HealthService.kt"
        const val PUBLISH_TIMEOUT_MINUTES = 30L
        val VERSION = Regex("""katachi\s*=\s*"([^"]+)"""")
    }
}
