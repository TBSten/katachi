package me.tbsten.katachi.intellij.uitest.pbt.authoring

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.DeclarationAt
import me.tbsten.katachi.intellij.model.DeclarationKind
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.declarations
import me.tbsten.katachi.intellij.presentation.authoring.Anchor
import me.tbsten.katachi.intellij.presentation.authoring.AnchorBinding
import me.tbsten.katachi.intellij.presentation.authoring.AnchorKind
import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.withTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * How anchors bind to declarations, over generated roles (a few names, several of them twice, entries in several groups, a
 * wildcard module key's copies of one site) and generated anchors (ide-authoring design 4.2, P-4):
 *
 * - the result has one entry per anchor, in order
 * - a bound declaration is of the anchor's kind and has its name
 * - what binds is exactly the declarations of that kind and name nearest the anchor's line, all of them on a tie
 * - an anchor whose name nothing declares binds nothing, whatever line it is on
 * - the copies of one declaration that a wildcard module key made are all bound or none
 * - the order the declarations come in does not change what binds
 */
class AnchorBindingPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261018L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (500 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private val arch = module(":arch").id
    private val file = "Roles.kt"
    private val names = listOf("Service", "Repository", "Screen")

    private class World(val declarations: List<DeclarationAt>, val anchors: List<Anchor>)

    private fun world(random: Random): World {
        val roles = (0 until random.nextInt(0, 7)).map { n ->
            val name = names[random.nextInt(names.size)]
            val copies = if (random.nextInt(4) == 0) random.nextInt(2, 4) else 1
            val line = random.nextInt(1, 50)
            val entries = (0 until copies).map { copy ->
                val entry = layoutEntry("m$copy/src/*$name.kt", if (random.nextInt(5) == 0) LayoutEntryKind.Directory else LayoutEntryKind.File, index = copy)
                    .declaredAt(file, line + 1)
                if (random.nextBoolean()) entry.withTemplate("g$n.$name", file, line + 2) else entry
            }
            roleModel("g$n.$name", *entries.toTypedArray()).declaredAt(file, line)
        }
        val declarations = definitionModel(*roles.toTypedArray()).declarations(arch)
        val anchors = (0 until random.nextInt(0, 8)).map {
            val kind = AnchorKind.entries[random.nextInt(AnchorKind.entries.size)]
            val name = names[random.nextInt(names.size)] + if (random.nextInt(6) == 0) "Typo" else ""
            Anchor(kind, if (kind == AnchorKind.Role) name.takeIf { random.nextInt(8) != 0 } else "*$name.kt", random.nextInt(1, 60))
        }
        return World(declarations, anchors)
    }

    private fun nameOf(declaration: DeclarationAt) = AnchorBinding.nameOf(declaration)

    private fun expected(declarations: List<DeclarationAt>, anchor: Anchor): List<DeclarationAt> {
        val named = declarations.filter { it.kind == anchor.kind.declares && (anchor.name == null || nameOf(it) == anchor.name) }
        val nearest = named.minOfOrNull { abs(it.site.line - anchor.line) } ?: return emptyList()
        return if (named.size == 1) named else named.filter { abs(it.site.line - anchor.line) == nearest }
    }

    @Test
    fun `結びつきは anchor ごとに1つで 同じ種類 同じ名前の宣言のうち最も近い行のもの全部`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { s ->
            val world = world(Random(s))
            val bound = AnchorBinding.bind(world.anchors, world.declarations)

            assertEquals("seed $s", world.anchors, bound.map { it.anchor })
            for (binding in bound) {
                assertEquals("seed $s ${binding.anchor}", expected(world.declarations, binding.anchor), binding.declarations)
                assertTrue("seed $s", binding.declarations.all { it.kind == binding.anchor.kind.declares })
                binding.anchor.name?.let { name -> assertTrue("seed $s", binding.declarations.all { nameOf(it) == name }) }
            }
        }
    }

    @Test
    fun `どの宣言とも名前の合わない anchor は 行がどこでも何も結ばない`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { s ->
            val random = Random(s)
            val world = world(random)
            val missing = Anchor(AnchorKind.entries[random.nextInt(AnchorKind.entries.size)], "NoSuchName", random.nextInt(-5, 100))

            assertEquals("seed $s", emptyList<DeclarationAt>(), AnchorBinding.bind(listOf(missing), world.declarations).single().declarations)
        }
    }

    @Test
    fun `ワイルドカードが増やした同じ場所の宣言は 全部結ばれるか 1つも結ばれない`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { s ->
            val world = world(Random(s))
            for (binding in AnchorBinding.bind(world.anchors, world.declarations)) {
                // A copy that is not a file entry (a directory of the same site) is never a name the source writes, so it is not one of the copies.
                val groups = world.declarations.filter { (it.kind == DeclarationKind.Entry || it.kind == DeclarationKind.Template) && it.entry?.kind == LayoutEntryKind.File }
                    .groupBy { Triple(it.kind, it.role?.qualifiedName, it.site) }
                for (copies in groups.values) {
                    val boundCount = copies.count { it in binding.declarations }
                    assertTrue("seed $s ${binding.anchor}", boundCount == 0 || boundCount == copies.size)
                }
            }
        }
    }

    @Test
    fun `宣言の並び順が変わっても 結ばれるものは同じ`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { s ->
            val random = Random(s)
            val world = world(random)
            val shuffled = world.declarations.shuffled(random)

            val a = AnchorBinding.bind(world.anchors, world.declarations).map { it.declarations.toSet() }
            val b = AnchorBinding.bind(world.anchors, shuffled).map { it.declarations.toSet() }

            assertEquals("seed $s", a, b)
        }
    }
}
