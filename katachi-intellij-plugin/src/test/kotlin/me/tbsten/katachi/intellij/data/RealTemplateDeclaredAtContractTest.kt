package me.tbsten.katachi.intellij.data

import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

/**
 * C-2 of ide-authoring (design 3.3): the `layout[].template.declaredAt.line` katachi really writes is the line of the
 * `.template` call in the definition file, so the completion's binding by line can rely on it. Read over the real
 * `definition.json` of each sample and the copy of that sample's definition sources the gutter tests use
 * (`contract/gutter/sources/<sample>`), as text: the line must hold `.template`.
 *
 * `DefinitionGutterRealShapeTest` covers the same thing through the PSI for `sample/jvm`'s `ServiceRole.kt` (a ◆ on the
 * `template` identifier of line 59).
 */
class RealTemplateDeclaredAtContractTest {
    private fun sourceFiles(sample: String): List<Path> {
        val root = Paths.get(javaClass.getResource("/contract/gutter/sources/$sample")?.toURI() ?: error("no sources for $sample"))
        return Files.walk(root).use { stream -> stream.filter { it.name.endsWith(".kt") }.toList() }
    }

    @Test
    fun `実物の definition json の template の declaredAt の行はどのサンプルでも template の呼び出しの行になる`() {
        for (sample in listOf("jvm", "android", "kmp")) {
            val sources = sourceFiles(sample)
            val refs = DefinitionFixtures.definition("real-$sample-definition").roles
                .flatMap { role -> role.layout.mapNotNull { it.template } }
            assertTrue("$sample: no .template in the real definition", refs.isNotEmpty())
            for (ref in refs) {
                val site = requireNotNull(ref.declaredAt) { "$sample: ${ref.specifier} has no declaredAt" }
                val file = sources.singleOrNull { it.name == site.fileName }
                    ?: error("$sample: ${site.fileName} is not exactly one file of the copied sources")
                val text = Files.readAllLines(file).getOrNull(site.line - 1)
                    ?: error("$sample: ${site.fileName} has no line ${site.line}")
                assertTrue("$sample: ${ref.specifier} is declared at ${site.fileName}:${site.line}, which is `${text.trim()}`", ".template" in text)
            }
        }
    }

    @Test
    fun `jvm の ServiceRole の template は 59 行目に宣言されている`() {
        val ref = DefinitionFixtures.definition("real-jvm-definition").roles
            .flatMap { role -> role.layout.mapNotNull { it.template } }
            .single { it.specifier == "domain.Service" }
        assertEquals(59, ref.declaredAt?.line)
    }
}
