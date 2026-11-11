package me.tbsten.katachi.intellij

import com.intellij.driver.sdk.openFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What the ◆ shows on files other than a Kotlin declaration (ide-quality design 2.2): the two `build.gradle.kts`, `README.md`
 * (a Markdown PSI registered with `language=""`), and a file whose role has no `ktFileContent` (`HealthRepository.kt`, one ◆ for
 * the file), and the Gutter Icons switch that turns the ◆ off in all four and back. Display only: Gradle runs for the
 * definition's load and nothing else, so a failure here is not mixed with a check or a generation.
 *
 * Written, compiled and not run by the build (`./gradlew integrationTest --tests '*GutterSurfacesSmokeTest*'` opens an IDE; run
 * by the verification lane, V4). Judged by eye: the ◆ drawn 0, 2, 5 and 10 seconds after it is found (screenshots).
 */
class GutterSurfacesSmokeTest {
    private val ide = SmokeIde("gutter-surfaces")

    @Test
    fun `build_gradle_ktsとREADMEとktFileContentの無い役割のファイルに◆が出て Gutter Iconsを切ると消え戻すと出る`() {
        val project = ide.prepareCopy()
        val files = listOf(ROOT_BUILD, ARCH_BUILD, README, REPOSITORY).map { project.resolve(it).toString() }
        val (rootBuild, archBuild, readme, repository) = files

        ide.run(project) { _, bridge ->
            // 1. build.gradle.kts: the Gradle.BuildScript role, loading the definition as the file opens.
            openFile(ROOT_BUILD, waitForCodeAnalysis = false)
            waitForSmoke("the ◆ on build.gradle.kts", bridge, rootBuild, timeout = 15.minutes) { "katachi: Gradle.BuildScript" in bridge.describeGutter(rootBuild) }
            bridge.shotsAfterGutter(ide.screenshots, "1-root-build")
            assertEquals(1, marksOf(bridge, rootBuild), bridge.describeGutter(rootBuild))

            // 2. architecture-test/build.gradle.kts: the same role, and the menu lists the root build script as the other file.
            openFile(ARCH_BUILD, waitForCodeAnalysis = false)
            waitForSmoke("the ◆ on architecture-test/build.gradle.kts", bridge, archBuild, timeout = 2.minutes) { "katachi: Gradle.BuildScript" in bridge.describeGutter(archBuild) }
            val archMark = bridge.describeGutter(archBuild).lines().first { it.startsWith("mark=") }
            val archLine = archMark.removePrefix("mark=").substringBefore('|').toInt()
            val menu = bridge.clickGutter(archBuild, archLine)
            assertTrue("Other files of Gradle.BuildScript (1)" in menu, menu)
            assertTrue(menu.lines().any { it.contains("build.gradle.kts") && !it.contains("architecture-test") }, menu)
            bridge.shot(ide.screenshots, "2-arch-build")

            // 3. README.md: one ◆, not two (the Kotlin provider and the other languages' one are not both drawn).
            openFile(README, waitForCodeAnalysis = false)
            waitForSmoke("the ◆ on README.md", bridge, readme, timeout = 2.minutes) { "katachi: Tool.Documentation" in bridge.describeGutter(readme) }
            assertEquals(1, marksOf(bridge, readme), bridge.describeGutter(readme))
            bridge.shot(ide.screenshots, "3-readme")

            // 4. A role without ktFileContent: one ◆ for the file, not one per declaration.
            openFile(REPOSITORY, waitForCodeAnalysis = false)
            waitForSmoke("the ◆ on HealthRepository.kt", bridge, repository, timeout = 2.minutes) { marksOf(bridge, repository) > 0 }
            assertEquals(1, marksOf(bridge, repository), bridge.describeGutter(repository))
            bridge.shot(ide.screenshots, "4-repository")

            // 5. The switch is one `getId()` shared by the Kotlin provider and the other one: off clears all four, on draws them again.
            // The daemon redraws the editor on screen only, so each file is brought to the front before it is waited for.
            assertEquals("gutterIcons=false", bridge.setGutterIcons(false))
            val openings = listOf(ROOT_BUILD, ARCH_BUILD, README, REPOSITORY)
            files.forEachIndexed { index, file ->
                openFile(openings[index], waitForCodeAnalysis = false)
                waitForSmoke("no ◆ on ${openings[index]} with Gutter Icons off", bridge, file, timeout = 1.minutes, interval = 2.seconds) { marksOf(bridge, file) == 0 }
                bridge.shot(ide.screenshots, "5-off-${index + 1}")
            }
            assertEquals("gutterIcons=true", bridge.setGutterIcons(true))
            files.forEachIndexed { index, file ->
                openFile(openings[index], waitForCodeAnalysis = false)
                waitForSmoke("the ◆ back on ${openings[index]} with Gutter Icons on", bridge, file, timeout = 2.minutes, interval = 2.seconds) { marksOf(bridge, file) == 1 }
                bridge.shot(ide.screenshots, "6-on-again-${index + 1}")
            }
        }
    }

    private fun marksOf(bridge: SmokeBridge, path: String): Int = bridge.describeGutter(path).lines().count { it.startsWith("mark=") }

    private companion object {
        const val ROOT_BUILD = "build.gradle.kts"
        const val ARCH_BUILD = "architecture-test/build.gradle.kts"
        const val README = "README.md"
        const val REPOSITORY = "src/main/kotlin/com/example/repository/HealthRepository.kt"
    }
}
