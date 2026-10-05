package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.model.ConstraintBackend
import me.tbsten.katachi.intellij.model.FileDeclarationsModel
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.testing.DEFINITION_ROOT
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import me.tbsten.katachi.intellij.testing.constraintModel
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths
import java.time.Instant

class DefinitionLookupTest {
    private val outer = definitionSnapshot(definitionModel(roleModel("Outer", layoutEntry("sub/a.kt"), layoutEntry("b.kt"))), module(":outer"))
    private val inner = definitionSnapshot(
        definitionModel(roleModel("Inner", layoutEntry("a.kt"))).copy(projectRoot = DEFINITION_ROOT.resolve("sub")),
        module(":inner"),
    )

    @Test
    fun `プロジェクトルートの下のファイルを相対パスで照合する`() {
        val roles = DefinitionLookup(listOf(outer)).forPath(DEFINITION_ROOT.resolve("b.kt"), Freshness.Fresh) ?: throw AssertionError()
        assertEquals("b.kt", roles.relativePath)
        assertEquals(DEFINITION_ROOT, roles.projectRoot)
        assertEquals(listOf("Outer"), roles.matches.map { it.role.qualifiedName })
    }

    @Test
    fun `入れ子のルートは深い方の定義で答え並びに依らない`() {
        for (definitions in listOf(listOf(outer, inner), listOf(inner, outer))) {
            val roles = DefinitionLookup(definitions).forPath(DEFINITION_ROOT.resolve("sub/a.kt"), Freshness.Fresh) ?: throw AssertionError()
            assertEquals(":inner", roles.module.gradlePath)
            assertEquals("a.kt", roles.relativePath)
            assertEquals(listOf("Inner"), roles.matches.map { it.role.qualifiedName })
        }
    }

    @Test
    fun `どのルートの下でもないファイルとルートそのものは null`() {
        val lookup = DefinitionLookup(listOf(outer))
        assertNull(lookup.forPath(Paths.get("/elsewhere/a.kt"), Freshness.Fresh))
        assertNull(lookup.forPath(DEFINITION_ROOT, Freshness.Fresh))
    }

    @Test
    fun `答えの古さは聞いたときのものを付け照合の結果は使い回す`() {
        val lookup = DefinitionLookup(listOf(outer))
        val stale = Freshness.Stale(Instant.EPOCH, StaleReason.DefinitionChanged)
        val fresh = lookup.forPath(DEFINITION_ROOT.resolve("x/unknown.kt"), Freshness.Fresh) ?: throw AssertionError()
        val again = lookup.forPath(DEFINITION_ROOT.resolve("x/unknown.kt"), stale) ?: throw AssertionError()
        assertEquals(stale, again.freshness)
        assertEquals(fresh.outcome, again.outcome)
        assertSame(fresh.matches, again.matches)
        assertEquals(MatchOutcome.UnexpectedUnder("x"), fresh.outcome)
    }

    @Test
    fun `ルートが無い定義はモジュールの Gradle ルートで照合する`() {
        val rootless = definitionSnapshot(definitionModel(roleModel("A", layoutEntry("a.kt"))).copy(projectRoot = null), module(":arch"))
        val roles = DefinitionLookup(listOf(rootless)).forPath(rootless.module.linkedRootPath.resolve("a.kt"), Freshness.Fresh)
        assertEquals(listOf("A"), roles?.matches?.map { it.role.qualifiedName })
    }

    @Test
    fun `宣言の説明は説明を書いた制約ごとに返し同じファイルが2つの役割にあれば2件`() {
        val explained = FileDeclarationsModel("src/A.kt", declarationsChecked = true, declarations = emptyList(), missing = emptyList(), unexplainedReason = null)
        val constraint = constraintModel(backend = ConstraintBackend.KtFileContent, files = listOf("src/*.kt"), fileDeclarations = listOf(explained))
        val snapshot = definitionSnapshot(
            definitionModel(
                roleModel("A", layoutEntry("src/*.kt"), constraints = listOf(constraint)),
                roleModel("B", layoutEntry("src/A.kt"), constraints = listOf(constraint, constraintModel(files = listOf("src/*.kt")))),
            ),
        )
        val lookup = DefinitionLookup(listOf(snapshot))
        val stale = Freshness.Stale(Instant.EPOCH, StaleReason.DefinitionChanged)
        val found = lookup.fileDeclarationsOf(DEFINITION_ROOT.resolve("src/A.kt"), stale)
        assertEquals(listOf("A", "B"), found.map { it.role.qualifiedName })
        assertEquals(listOf(stale, stale), found.map { it.freshness })
        assertEquals(emptyList<Any>(), lookup.fileDeclarationsOf(DEFINITION_ROOT.resolve("src/B.kt"), Freshness.Fresh))
    }

    @Test
    fun `生成された fixture の matchedFiles はすべて同じ役割に当たる`() {
        for (name in DefinitionFixtures.definitionNames()) {
            val definition = DefinitionFixtures.definition(name)
            val root = definition.projectRoot ?: continue
            val lookup = DefinitionLookup(listOf(definitionSnapshot(definition)))
            for (role in definition.roles) {
                for (entry in role.layout) {
                    for (file in entry.matchedFiles.take(20)) {
                        val roles = lookup.forPath(root.resolve(file), Freshness.Fresh) ?: throw AssertionError("$name: $file")
                        assertTrue("$name: $file is not ${role.qualifiedName}: ${roles.matches}", roles.matches.any { it.role.qualifiedName == role.qualifiedName })
                    }
                }
            }
        }
    }
}
