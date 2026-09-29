package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.indexOf
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.modulePlacedTemplate
import me.tbsten.katachi.intellij.testing.snapshotOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The placement index over a template below a module capture that katachi describes
 * (`modulePlacements`): matched against its path in each existing module, not its declared pattern.
 */
class ModulePlacementIndexTest {
    /** `:feature:home` where the convention puts it; `:feature:settings` where katachi's ModuleResolver moved it. */
    private val component = modulePlacedTemplate(
        pattern = "feature/\${feature}/src/<feature>/<feature>\${name}.kt",
        modules = mapOf(
            "home" to ("feature/home" to "feature/home/src/home/Home\${name}.kt"),
            "settings" to ("apps/settings-screen" to "apps/settings-screen/src/settings/Settings\${name}.kt"),
        ),
    )
    private val index = indexOf(snapshotOf(module(":arch"), listOf(component)))

    private data class Hit(val decided: Map<String, String>, val undecided: List<String>, val remaining: String, val targetUndecided: Boolean)

    private fun hits(matches: List<PlacementMatch>) = matches.map { Hit(it.decided, it.undecided, it.remainingPath, it.targetUndecided) }

    @Test
    fun `モジュールの中のファイルはそのモジュールの値と名前が決まる`() {
        assertEquals(
            listOf(Hit(mapOf("feature" to "home", "name" to "Card"), emptyList(), "", false)),
            hits(index.matchesForFile(ROOT.resolve("feature/home/src/home/HomeCard.kt"))),
        )
    }

    @Test
    fun `ディレクトリを変えたモジュールの中ではそのディレクトリで当たり規約どおりの場所では当たらない`() {
        assertEquals(
            listOf(Hit(mapOf("feature" to "settings", "name" to "Card"), emptyList(), "", false)),
            hits(index.matchesForFile(ROOT.resolve("apps/settings-screen/src/settings/SettingsCard.kt"))),
        )
        assertEquals(emptyList<Hit>(), hits(index.matchesForFile(ROOT.resolve("feature/settings/src/settings/SettingsCard.kt"))))
        assertEquals(
            listOf(Hit(mapOf("feature" to "settings"), listOf("name"), "settings/Settings<name>.kt", false)),
            hits(index.matchesForDirectory(ROOT.resolve("apps/settings-screen/src"))),
        )
    }

    @Test
    fun `今あるモジュールに無いディレクトリやファイルには当たらない`() {
        assertEquals(emptyList<Hit>(), hits(index.matchesForDirectory(ROOT.resolve("feature/profile"))))
        assertEquals(emptyList<Hit>(), hits(index.matchesForFile(ROOT.resolve("feature/profile/src/profile/ProfileCard.kt"))))
    }

    @Test
    fun `モジュールの中のディレクトリでは残りのパスがkatachiの綴りで出て生成先は決まる`() {
        assertEquals(
            listOf(Hit(mapOf("feature" to "home"), listOf("name"), "src/home/Home<name>.kt", false)),
            hits(index.matchesForDirectory(ROOT.resolve("feature/home"))),
        )
    }

    @Test
    fun `下に今あるモジュールが1つだけのディレクトリではそのモジュールに決まる`() {
        // feature/ holds :feature:home only; :feature:settings lives under apps/.
        assertEquals(
            listOf(Hit(mapOf("feature" to "home"), listOf("name"), "home/src/home/Home<name>.kt", false)),
            hits(index.matchesForDirectory(ROOT.resolve("feature"))),
        )
    }

    @Test
    fun `下に今あるモジュールが2つ以上あるディレクトリではどれか決めず宣言のパターンで当たる`() {
        assertEquals(
            listOf(Hit(emptyMap(), listOf("feature", "name"), "feature/<feature>/src/<feature>/<feature><name>.kt", true)),
            hits(index.matchesForDirectory(ROOT)),
        )
    }

    @Test
    fun `ダイアログの初期値はモジュールの値も含む`() {
        val id = index.matchesForFile(ROOT.resolve("feature/home/src/home/HomeCard.kt")).single().id
        assertEquals(
            mapOf("feature" to "home", "name" to "Card"),
            index.seedsFor(EntryOrigin.EditorFile(ROOT.resolve("feature/home/src/home/HomeCard.kt")), id),
        )
    }
}
