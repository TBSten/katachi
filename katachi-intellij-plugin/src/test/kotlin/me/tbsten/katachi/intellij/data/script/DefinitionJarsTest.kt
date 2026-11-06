package me.tbsten.katachi.intellij.data.script

import junit.framework.TestCase
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

/** The check of the CLI's jars before the Kotlin plugin reads them (KATACHI-101). */
internal class DefinitionJarsTest : TestCase() {
    private lateinit var dir: Path

    override fun setUp() {
        super.setUp()
        dir = createTempDirectory("katachi-jars-")
    }

    override fun tearDown() {
        try {
            dir.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun jar(name: String, vararg entries: String): Path {
        val jar = dir.resolve(name)
        JarOutputStream(jar.outputStream()).use { out ->
            for (entry in entries) {
                out.putNextEntry(ZipEntry(entry))
                out.closeEntry()
            }
        }
        return jar
    }

    fun `test 目印のある jar が 1 つあれば読める`() {
        val jars = listOf(jar("katachi-script.jar", KATACHI_SCRIPT_DEFINITION_MARKER), jar("katachi.jar", "me/tbsten/katachi/dsl/Architecture.class"))
        assertNull(definitionJarsProblem(jars))
    }

    fun `test 目印がどこにも無ければ理由を返す`() {
        val problem = definitionJarsProblem(listOf(jar("other.jar", "META-INF/kotlin/script/templates/com.example.Other.classname")))
        assertTrue("$problem", problem.orEmpty().startsWith("no *.katachi.kts definition"))
    }

    fun `test jar として開けないファイルがあれば理由を返す`() {
        val notJar = dir.resolve("katachi-script.jar").also { it.writeText("not a jar") }
        val problem = definitionJarsProblem(listOf(jar("katachi.jar", KATACHI_SCRIPT_DEFINITION_MARKER), notJar))
        assertTrue("$problem", problem.orEmpty().startsWith("not a jar"))
    }
}
