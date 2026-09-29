package me.tbsten.katachi.intellij.ide.notification

import com.intellij.ui.EditorNotificationPanel
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest

/**
 * Verification of the editor notification on a file that several templates fit: same pattern in two
 * roles, one role with several ids, partly overlapping captures, two definitions. One panel, one
 * wording, one link, and the link goes to the first template of the index order.
 */
internal class MultiTemplateNotificationTest : EditorNotificationTestBase() {

    /** [placementJson] with an id per template: `role.id` is the specifier, the id the title (as katachi prints). */
    private fun idJson(vararg templates: Triple<String, String?, String>): String {
        val summaries = templates.joinToString(",") { (role, id, pattern) -> summary(role, id, pattern) }
        val details = templates.joinToString(",") { (role, id, pattern) -> detail(role, id, pattern) }
        return """{"templates":[$summaries],"details":[$details]}"""
    }

    private val capture = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}""")

    private fun specifier(role: String, id: String?) = if (id == null) role else "$role.$id"

    private fun captures(pattern: String): String {
        val segments = pattern.split('/')
        val glob = pattern.replace(capture, "*")
        return capture.findAll(pattern).map { it.groupValues[1] }.distinct().joinToString(",") { name ->
            val position = segments.indexOfFirst { "\${$name}" in it }
            """{"name":"$name","kind":"PathCapture","pattern":"$glob","segment":"${segments[position]}","position":$position}"""
        }
    }

    private fun summary(role: String, id: String?, pattern: String): String {
        val idJson = id?.let { "\"$it\"" } ?: "null"
        return """{"template":"${specifier(role, id)}","id":$idJson,"title":"${id ?: role}","roleName":"$role","summary":null,"parameterNames":[],"conflict":false,"captures":[${captures(pattern)}]}"""
    }

    private fun detail(role: String, id: String?, pattern: String): String {
        val idJson = id?.let { "\"$it\"" } ?: "null"
        val names = capture.findAll(pattern).map { "\"${it.groupValues[1]}\"" }.distinct().joinToString(",")
        return """{"template":"${specifier(role, id)}","id":$idJson,"title":"${id ?: role}","roleName":"$role","summary":null,"parameters":[],""" +
            """"files":[{"pattern":"$pattern","fileName":"${pattern.substringAfterLast('/')}","path":"$pattern","captures":[$names],"parameters":[],"content":""}],""" +
            """"branches":[],"exampleCommand":"./gradlew katachiTemplate --arg template=${specifier(role, id)}","captures":[${captures(pattern)}]}"""
    }

    /** The platform keeps one panel per provider and file editor, so "how many" is read from its own model: 1 or 0. */
    private fun panelsIn(editor: com.intellij.openapi.fileEditor.FileEditor): List<EditorNotificationPanel> = listOfNotNull(panelOf(editor))

    private val emptyText = "package com.example\n"
    private val contentText = "package com.example\n\nclass Existing\n"

    fun `test 同じ役割の id 違いが同じパターンに当たっても通知は1本で文言も1つ`() {
        loadIndex(idJson(Triple("data.Repository", "user", "data/\${name}Repository.kt"), Triple("data.Repository", "settings", "data/\${name}Repository.kt")))
        val rel = "data/CacheRepository.kt"
        assertEquals(listOf("user", "settings"), matchesOf(rel).map { it.id.template.substringAfterLast('.') })

        val emptyEditor = open(rel, emptyText)
        val panel = panelOf(emptyEditor)!!
        assertEquals(KatachiBundle.message("notification.editor.empty"), panel.text)
        assertEquals(1, panel.links.size)
        assertEquals(1, panelsIn(emptyEditor).size)
        panel.link("notification.editor.create").doClick()
        assertEquals(listOf(GenerateDialogRequest(EntryOrigin.EditorFile(path(rel)), matchesOf(rel).first().id, mapOf("name" to "Cache"))), effects.dialogs)

        val contentEditor = open("data/OtherRepository.kt", contentText)
        val panel2 = panelOf(contentEditor)!!
        assertEquals(KatachiBundle.message("notification.editor.matched"), panel2.text)
        assertEquals(1, panel2.links.size)
        panel2.link("notification.editor.view").doClick()
        assertEquals(listOf(matchesOf("data/OtherRepository.kt").first().id), effects.revealed)
    }

    fun `test 部分一致の capture が重なる3つのテンプレートでも通知は1本で最初のテンプレートと値が行き先になる`() {
        loadIndex(
            idJson(
                Triple("data.Repository", "user", "data/User\${name}Repository.kt"),
                Triple("data.Any", null, "data/\${name}Repository.kt"),
                Triple("data.Impl", null, "data/User\${name}RepositoryImpl.kt"),
            ),
        )
        val rel = "data/UserFooRepository.kt"
        val matches = matchesOf(rel)
        assertEquals(listOf("data.Repository.user", "data.Any"), matches.map { it.id.template })
        // The same file gives the two templates different values for `name`: what the dialog opens with is the first one's.
        assertEquals(listOf("Foo", "UserFoo"), matches.map { it.decided["name"] })

        val editor = open(rel, emptyText)
        val panel = panelOf(editor)!!
        assertEquals(KatachiBundle.message("notification.editor.empty"), panel.text)
        assertEquals(1, panel.links.size)
        panel.link("notification.editor.create").doClick()
        assertEquals("data.Repository.user", effects.dialogs.single().initialTemplate.template)
        assertEquals(mapOf("name" to "Foo"), effects.dialogs.single().seeds)
        assertEquals(1, effects.dialogs.size)
    }

    fun `test 2つの定義に同じテンプレートがある中身ありのファイルでも通知は1本で最初の定義のテンプレートを見せる`() {
        synced = syncedWith(":arch-a", ":arch-b")
        loadIndex(idJson(Triple("data.Repository", null, "data/\${name}Repository.kt")))
        val rel = "data/CacheRepository.kt"
        assertEquals(listOf(":arch-a", ":arch-b"), matchesOf(rel).map { it.definition.gradlePath })

        val editor = open(rel, contentText)
        val panel = panelOf(editor)!!
        assertEquals(KatachiBundle.message("notification.editor.matched"), panel.text)
        assertEquals(1, panel.links.size)
        panel.link("notification.editor.view").doClick()
        assertEquals(":arch-a", effects.revealed.single().module.gradlePath)
        assertEquals(1, panelsIn(editor).size)
    }

    fun `test 更新を繰り返しても複数当たりの通知は二重にならない`() {
        loadIndex(idJson(Triple("a.One", null, "data/\${name}Repository.kt"), Triple("a.Two", null, "data/\${name}Repository.kt")))
        val editor = open("data/CacheRepository.kt", emptyText)
        repeat(3) { updateAll() }
        assertEquals(1, panelsIn(editor).size)
        assertEquals(KatachiBundle.message("notification.editor.empty"), panelOf(editor)!!.text)
    }
}
