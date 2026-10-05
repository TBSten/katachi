package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.testing.indexOf
import me.tbsten.katachi.intellij.testing.editorFile
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.modulePlacedTemplate
import me.tbsten.katachi.intellij.testing.patternTemplate
import me.tbsten.katachi.intellij.testing.snapshotOf
import me.tbsten.katachi.intellij.testing.underRoot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The captures a sibling file decides for the next file (`EntryOrigin.SiblingOf`, design 2.3, U-9): what the file's path says
 * about the directories above it, without the part that names the file.
 */
class SiblingSeedsTest {
    private val arch = module(":arch")
    private val screen = patternTemplate("feature.Screen", "feature/\${feature}/ui/\${name}Screen.kt")
    private val index = indexOf(snapshotOf(arch, listOf(screen)))
    private val id = TemplateId(arch.id, screen.template)

    private fun siblingSeeds(relative: String, template: TemplateId = id) = index.seedsFor(EntryOrigin.SiblingOf(underRoot(relative)), template)

    @Test
    fun `ファイル名の区切りにある capture は引き継がず 上のディレクトリの capture は引き継ぐ`() {
        assertEquals(mapOf("feature" to "home"), siblingSeeds("feature/home/ui/HomeScreen.kt"))
    }

    @Test
    fun `編集中のファイルから開いたときは名前まで埋まる 兄弟から開いたときとの違いは名前だけ`() {
        val editor = index.seedsFor(editorFile("feature/home/ui/HomeScreen.kt"), id)
        assertEquals(mapOf("feature" to "home", "name" to "Home"), editor)
        assertEquals(editor - "name", siblingSeeds("feature/home/ui/HomeScreen.kt"))
    }

    @Test
    fun `ファイル名の区切りが上のディレクトリと同じ capture を使うなら 引き継ぐ`() {
        val shared = patternTemplate("feature.Shared", "feature/\${feature}/\${feature}Screen.kt")
        val sharedIndex = indexOf(snapshotOf(arch, listOf(shared)))
        val sharedId = TemplateId(arch.id, shared.template)
        assertEquals(mapOf("feature" to "home"), sharedIndex.seedsFor(EntryOrigin.SiblingOf(underRoot("feature/home/homeScreen.kt")), sharedId))
    }

    @Test
    fun `ファイル名の区切りに複数の capture があれば どちらも外す`() {
        val two = patternTemplate("feature.Pair", "feature/\${feature}/\${prefix}And\${suffix}.kt")
        val twoIndex = indexOf(snapshotOf(arch, listOf(two)))
        val twoId = TemplateId(arch.id, two.template)
        assertEquals(mapOf("feature" to "home"), twoIndex.seedsFor(EntryOrigin.SiblingOf(underRoot("feature/home/AAndB.kt")), twoId))
    }

    @Test
    fun `テンプレートの置き場に合わないファイルや知らないテンプレートでは空`() {
        assertEquals(emptyMap<String, String>(), siblingSeeds("other/home/ui/HomeScreen.kt"))
        assertEquals(emptyMap<String, String>(), siblingSeeds("feature/home/ui/Home.kt"))
        assertEquals(emptyMap<String, String>(), siblingSeeds("feature/home/ui/HomeScreen.kt", TemplateId(arch.id, "no.Such")))
        assertEquals(emptyMap<String, String>(), siblingSeeds("feature/home/ui"))
    }

    @Test
    fun `モジュールの capture は兄弟のいるモジュールのものを引き継ぐ`() {
        val placed = modulePlacedTemplate(modules = mapOf("home" to ("feature/home" to "feature/home/src/Home\${name}.kt"), "settings" to ("feature/settings" to "feature/settings/src/Settings\${name}.kt")))
        val placedIndex = indexOf(snapshotOf(arch, listOf(placed)))
        val placedId = TemplateId(arch.id, placed.template)
        assertEquals(mapOf("feature" to "settings"), placedIndex.seedsFor(EntryOrigin.SiblingOf(underRoot("feature/settings/src/SettingsScreen.kt")), placedId))
        assertEquals(emptyMap<String, String>(), placedIndex.seedsFor(EntryOrigin.SiblingOf(underRoot("feature/unknown/src/UnknownScreen.kt")), placedId))
    }

    @Test
    fun `どの兄弟からでも 外す capture は変わらない`() {
        for (feature in listOf("home", "settings", "a-b")) {
            for (name in listOf("A", "Profile", "XyZ")) {
                assertEquals(mapOf("feature" to feature), siblingSeeds("feature/$feature/ui/${name}Screen.kt"))
            }
        }
    }
}
