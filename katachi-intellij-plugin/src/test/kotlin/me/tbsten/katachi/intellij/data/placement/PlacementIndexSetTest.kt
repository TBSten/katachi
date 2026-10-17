package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.model.TemplateSetMemberModel
import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability
import me.tbsten.katachi.intellij.presentation.entry.NewMenuNode
import me.tbsten.katachi.intellij.presentation.entry.newMenuTreeOf
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.indexOf
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.patternTemplate
import me.tbsten.katachi.intellij.testing.setMember
import me.tbsten.katachi.intellij.testing.setTemplate
import me.tbsten.katachi.intellij.testing.snapshotOf
import me.tbsten.katachi.intellij.testing.stringParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A template set in the placement index and the New menu tree (template-set design 8.3, 8.4). */
class PlacementIndexSetTest {
    private val arch = module(":arch")
    private val newScreen = setTemplate(
        "feature.NewScreen",
        listOf(
            setMember("feature.Screen", "Screen", "feature/\${feature}/ui/\${name}Screen.kt"),
            setMember("feature.ViewModel", "ViewModel", "feature/\${feature}/vm/\${name}ViewModel.kt"),
        ),
        title = "新しい画面",
    )
    private val screen = patternTemplate("feature.Screen", "feature/\${feature}/ui/\${name}Screen.kt")
    private val index = indexOf(snapshotOf(arch, listOf(screen, newScreen)))

    private fun setIds(matches: List<me.tbsten.katachi.intellij.presentation.entry.PlacementMatch>) =
        matches.filter { it.isSet }.map { it.template.template.template }

    @Test
    fun `最初のメンバのパターンに合うディレクトリで組が出て決まる値は組のパラメータ名になる`() {
        val matches = index.matchesForDirectory(ROOT.resolve("feature/home"))

        val set = matches.single { it.isSet }
        assertEquals("feature.NewScreen", set.template.template.template)
        assertEquals(mapOf("feature" to "home"), set.decided)
        assertEquals(listOf("name"), set.undecided)
    }

    @Test
    fun `2番目のメンバだけが合うディレクトリには組が出ない`() {
        // feature/home/vm fits the ViewModel member only.
        assertEquals(emptyList<String>(), setIds(index.matchesForDirectory(ROOT.resolve("feature/home/vm"))))
    }

    @Test
    fun `組の名前に置き換えられなかったファイルの capture は決まらず未決に回る`() {
        // The member's `name` is the module's directory name; the set's own `name` means something else, so it must not be seeded.
        val unexpressed = TemplateSetMemberModel(
            "feature.Screen",
            "Screen",
            emptyList(),
            listOf(file("Screen.kt", path = null, pattern = "feature/\${name}/ui/Screen.kt", captures = emptyList())),
        )
        val set = setTemplate("feature.NewThing", listOf(unexpressed), parameters = listOf(stringParam("name")))
        val index = indexOf(snapshotOf(arch, listOf(set)))

        val match = index.matchesForDirectory(ROOT.resolve("feature")).single { it.isSet }

        assertEquals(emptyMap<String, String>(), match.decided)
        assertEquals(listOf("name"), match.undecided)
        assertEquals(emptyMap<String, String>(), index.seedsFor(me.tbsten.katachi.intellij.presentation.entry.EntryOrigin.NewMenuDirectory(ROOT.resolve("feature/home")), match.id))
    }

    @Test
    fun `ファイルの照合には組が出ず通知もガターも組を勧めない`() {
        val matches = index.matchesForFile(ROOT.resolve("feature/home/ui/HomeScreen.kt"))

        assertEquals(listOf("feature.Screen"), matches.map { it.template.template.template })
        assertTrue(matches.none { it.isSet })
    }

    @Test
    fun `プレビューが失敗した組と最初のメンバにファイルが無い組は出ない`() {
        val broken = setTemplate("feature.Broken", listOf(setMember("feature.Screen", "Screen", "feature/\${feature}/ui/\${name}Screen.kt")), unavailable = true)
        val empty = setTemplate("feature.NoFiles", listOf(setMember("feature.Screen", "Screen")))
        val index = indexOf(snapshotOf(arch, listOf(broken, empty)))

        assertEquals(emptyList<String>(), index.matchesForDirectory(ROOT.resolve("feature/home")).map { it.template.template.template })
    }

    @Test
    fun `New メニューの木では組が group の下に組の題の1項目として入る`() {
        val matches = index.matchesForDirectory(ROOT.resolve("feature/home"))

        val tree = newMenuTreeOf(EntryAvailability.Ready, matches)

        val group = tree.single() as NewMenuNode.Group
        assertEquals("feature", group.name)
        val leaves = group.children.map { it as NewMenuNode.Template }
        assertEquals(listOf("新しい画面" to true), leaves.filter { it.match.isSet }.map { it.label to it.match.isSet })
        // The template next to it keeps its remaining path in the label.
        assertTrue(leaves.single { !it.match.isSet }.label.startsWith("Screen"))
    }
}
