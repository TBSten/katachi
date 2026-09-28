package me.tbsten.katachi.intellij.uitest.pbt.entry

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability
import me.tbsten.katachi.intellij.presentation.entry.NewMenuNode
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import me.tbsten.katachi.intellij.presentation.entry.newMenuTreeOf
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.placementMatch
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The New menu tree over generated matches: nested groups, roles at the root, roles with one and
 * with several templates, one or two definitions, and the same match listed twice.
 * `-Pkatachi.pbt.seed=N` tries another seed, `-Pkatachi.pbt.scale=N` runs N times as many cases.
 */
class NewMenuTreePropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20260929L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0

    private fun config(iterations: Int) = PropTestConfig(seed = seed, iterations = (iterations * scale).toInt().coerceAtLeast(1), shrinkingMode = ShrinkingMode.Bounded(400))

    private val definitions = listOf(module(":arch-a"), module(":arch-b"))
    private val groupNames = listOf("data", "domain", "model", "ui", "value")
    private val roleNames = listOf("Repository", "Screen", "Entity", "Readme")

    /** Role paths: 0 to 3 nested groups, then a role. Ids 0..2 give a role several templates. */
    private val matchesArb: Arb<List<PlacementMatch>> = arbitrary { rs ->
        val r = rs.random
        val count = r.nextInt(0, 14)
        val useTwoDefinitions = r.nextBoolean()
        List(count) {
            val groups = List(r.nextInt(0, 4)) { groupNames[r.nextInt(groupNames.size)] }
            val role = (groups + roleNames[r.nextInt(roleNames.size)]).joinToString(".")
            val id = r.nextInt(0, 3).takeIf { it > 0 }?.toString()
            val remaining = if (r.nextBoolean()) "" else "sub/<name>.kt"
            placementMatch(template(role, id = id), definitions[if (useTwoDefinitions) r.nextInt(2) else 0], remainingPath = remaining)
        }
    }

    private fun leaves(nodes: List<NewMenuNode>): List<NewMenuNode.Template> = nodes.flatMap {
        when (it) {
            is NewMenuNode.Definition -> leaves(it.children)
            is NewMenuNode.Group -> leaves(it.children)
            is NewMenuNode.Role -> leaves(it.children)
            is NewMenuNode.Template -> listOf(it)
            NewMenuNode.Loading -> emptyList()
        }
    }

    /** Every node with the names of the groups and role above it, and the definition. */
    private fun walk(nodes: List<NewMenuNode>, path: List<String> = emptyList(), definition: KatachiModule? = null, visit: (NewMenuNode, List<String>, KatachiModule?) -> Unit) {
        for (node in nodes) {
            visit(node, path, definition)
            when (node) {
                is NewMenuNode.Definition -> walk(node.children, path, node.definition, visit)
                is NewMenuNode.Group -> walk(node.children, path + node.name, definition, visit)
                is NewMenuNode.Role -> walk(node.children, path + node.name, definition, visit)
                is NewMenuNode.Template, NewMenuNode.Loading -> Unit
            }
        }
    }

    // covers: 論点7, 論点12
    @Test
    fun `どんな当たりでも木は葉が当たりの数で重複が無く空のノードが無く定義の段は2つ以上のときだけ`() = runBlocking<Unit> {
        checkAll(config(500), matchesArb) { matches ->
            val tree = newMenuTreeOf(EntryAvailability.Ready, matches)
            val wanted = matches.distinctBy { it.id }
            val found = leaves(tree)

            assertEquals("leaves = matches", wanted.map { it.id }.toSet(), found.map { it.match.id }.toSet())
            assertEquals("no template twice", wanted.size, found.size)

            val hasDefinitionLevel = tree.any { it is NewMenuNode.Definition }
            assertEquals("definition level", wanted.map { it.definition }.distinct().size >= 2, hasDefinitionLevel)
            if (hasDefinitionLevel) assertEquals("only definitions at the top", true, tree.all { it is NewMenuNode.Definition })

            walk(tree) { node, path, definition ->
                when (node) {
                    is NewMenuNode.Group -> assertEquals("group has children: $node", false, node.children.isEmpty())
                    is NewMenuNode.Role -> {
                        assertEquals("a role with one template is the item, not nested: $node", true, node.children.size >= 2)
                        assertEquals(true, node.children.all { it is NewMenuNode.Template })
                    }
                    is NewMenuNode.Definition -> assertEquals(false, node.children.isEmpty())
                    is NewMenuNode.Template -> {
                        val role = node.match.template.template.roleName.split('.')
                        assertEquals("groups above a leaf are its role's groups", role.dropLast(1), path.take(role.size - 1))
                        if (hasDefinitionLevel) assertEquals(node.match.definition, definition)
                        // Under a role node the path has the role as its last name; a lone role's item stands where the role would.
                        assertEquals(true, path.size == role.size - 1 || path == role)
                    }
                    NewMenuNode.Loading -> error("no loading item once ready")
                }
            }
        }
    }

    // covers: 論点18
    @Test
    fun `読み込み前と読み込み中は当たりによらず押せない1項目だけで使えないときは空`() = runBlocking<Unit> {
        checkAll(config(100), matchesArb) { matches ->
            assertEquals(listOf(NewMenuNode.Loading), newMenuTreeOf(EntryAvailability.NotLoaded, matches))
            assertEquals(listOf(NewMenuNode.Loading), newMenuTreeOf(EntryAvailability.Loading, matches))
            assertEquals(emptyList<NewMenuNode>(), newMenuTreeOf(EntryAvailability.Unavailable, matches))
        }
    }

    // covers: 論点12
    @Test
    fun `当たりの並びを変えても葉の集合は変わらない`() = runBlocking<Unit> {
        checkAll(config(200), matchesArb) { matches ->
            val a = leaves(newMenuTreeOf(EntryAvailability.Ready, matches)).map { it.match.id }.toSet()
            val b = leaves(newMenuTreeOf(EntryAvailability.Ready, matches.reversed())).map { it.match.id }.toSet()
            assertEquals(a, b)
        }
    }
}
