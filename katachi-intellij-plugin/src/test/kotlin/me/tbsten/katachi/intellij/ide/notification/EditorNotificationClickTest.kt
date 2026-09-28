package me.tbsten.katachi.intellij.ide.notification

import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest

/** What the notification's links and buttons ask of the IDE when pressed (issues 0, 14, 15, 17). */
internal class EditorNotificationClickTest : EditorNotificationTestBase() {

    // covers: 論点0
    // covers: 論点17
    fun `test 作成するを押すと索引の並びで最初のテンプレートと起点の全 capture でダイアログを開く`() {
        loadIndex()
        val relative = "feature/home/ui/HomeScreen.kt"
        val matches = matchesOf(relative)
        assertEquals(listOf("feature.Screen", "feature.AnyUi"), matches.map { it.id.template })

        panelOf(open(relative, "package com.example\n"))!!.link("notification.editor.create").doClick()

        assertEquals(listOf("dialog"), effects.log)
        assertEquals(
            listOf(GenerateDialogRequest(EntryOrigin.EditorFile(path(relative)), matches.first().id, mapOf("feature" to "home", "name" to "Home"))),
            effects.dialogs,
        )
    }

    // covers: 論点17
    fun `test テンプレートを見るを押すと索引の並びで最初のテンプレートをツールウィンドウで見せる`() {
        loadIndex()
        val relative = "feature/home/ui/HomeScreen.kt"

        panelOf(open(relative, "class HomeScreen\n"))!!.link("notification.editor.view").doClick()

        assertEquals(listOf("reveal"), effects.log)
        assertEquals(listOf(matchesOf(relative).first().id), effects.revealed)
    }

    // covers: 論点14
    fun `test 歯車を押すと設定を開く`() {
        loadIndex()

        panelOf(open("feature/home/ui/Home.kt", ""))!!.button("notification.editor.settings.tooltip").doClick()

        assertEquals(listOf("settings"), effects.log)
    }

    // covers: 論点15
    fun `test バツを押すとそのファイルの通知が消え updateAllNotifications の後も戻らない`() {
        loadIndex()
        val editor = open("feature/home/ui/Home.kt", "")

        panelOf(editor)!!.button("notification.editor.dismiss.tooltip").doClick()

        assertNull(panelOf(editor))
        updateAll()
        assertNull(panelOf(editor))
        assertTrue(effects.log.isEmpty())
    }

    // covers: 論点17
    fun `test 押したときに入口の効果が投げても操作は失敗にならず次も押せる`() {
        loadIndex()
        val panel = panelOf(open("feature/home/ui/Home.kt", ""))!!
        entryEffects.failNext = true

        panel.link("notification.editor.create").doClick()
        panel.link("notification.editor.create").doClick()

        assertEquals(listOf("dialog"), effects.log)
    }
}
