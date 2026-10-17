package me.tbsten.katachi.intellij.ide.authoring.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.authoring.AuthoringPorts
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness

/**
 * The names of the definition offered inside its strings, in a real editor over a fake definition: `captureValue("…")`,
 * a `templateSet`'s `template("…")` and `arg("…")`. The definition's answer is scripted (a screen template with the
 * captures `feature`, `name`, `nameOf`, a view model template), and the site of the `.template { }` of the open file
 * is the one the fake resolver says is in it.
 */
internal class DefinitionCompletionTest : AnalysisTestBase() {
    private lateinit var service: FakeDefinitionModelService

    override fun setUp() {
        super.setUp()
        service = FakeDefinitionModelService()
        project.replaceService(AuthoringPorts::class.java, AuthoringPorts { service }, testRootDisposable)
        service.emit(DefinitionState.Ready(listOf(AuthoringFixtures.snapshotWithSiteAt(2)), Freshness.Fresh))
    }

    /**
     * Opens `ScreenRole.kt` with [text] inside a function (a definition's statements are not top level), so that the first
     * line of [text] is line 2, and lets the fake resolver say that the site on line 2 is in this file.
     */
    private fun open(text: String) {
        myFixture.configureByText("ScreenRole.kt", AuthoringFixtures.inFunction(text.trimIndent()))
        service.resolveWith(DeclarationSiteRef("ScreenRole.kt", 2), ResolvedSite.Found(myFixture.file.virtualFile, 2))
    }

    /** What completing offers, without the open file's own name, which the platform's file references add inside any path-like string. */
    private fun offered(): List<String> {
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty().filter { it != "ScreenRole.kt" }
    }

    fun `test captureValue の引用符の中で補完すると capture の名前が出る`() {
        open(
            """
            "*Screen.kt".file().template {
                captureValue("<caret>")
            }
            """,
        )

        assertEquals(listOf("feature", "name", "nameOf"), offered())
    }

    fun `test 打った文字で候補が絞られ 1つなら確定する`() {
        open(
            """
            "*Screen.kt".file().template {
                captureValue("fe<caret>")
            }
            """,
        )

        assertEquals(emptyList<String>(), offered())
        myFixture.checkResult(AuthoringFixtures.inFunction("\"*Screen.kt\".file().template {\n    captureValue(\"feature\")\n}"))
    }

    fun `test Tab で選ぶと引用符の中身が全部置き換わる`() {
        open(
            """
            "*Screen.kt".file().template {
                captureValue("na<caret>me2")
            }
            """,
        )

        assertEquals(listOf("name", "nameOf"), offered())
        myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)

        myFixture.checkResult(AuthoringFixtures.inFunction("\"*Screen.kt\".file().template {\n    captureValue(\"name\")\n}"))
    }

    fun `test capture の候補には種類とどのテンプレートかが付く`() {
        open(
            """
            "*Screen.kt".file().template {
                captureValue("<caret>")
            }
            """,
        )

        myFixture.completeBasic()
        val shown = myFixture.lookup.items.associate { item ->
            val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
            item.renderElement(presentation)
            item.lookupString to (presentation.typeText to presentation.tailText)
        }

        assertEquals("path capture" to " (ui.Screen)", shown["feature"])
        assertEquals("module capture" to " (ui.Screen)", shown["name"])
    }

    fun `test 候補を出さない場所 capture 以外の関数 template の外 補間 raw string`() {
        open(
            """
            "*Screen.kt".file().template {
                stringParameter("<caret>")
            }
            """,
        )
        assertEquals(emptyList<String>(), offered().filter { it in CAPTURES })

        open("val a = captureValue(\"<caret>\")")
        assertEquals(emptyList<String>(), offered().filter { it in CAPTURES })

        // The id of a layout file's template is a string of its own, not a template's specifier.
        open(
            """
            "*Screen.kt".file().template("<caret>") { "" }
            """,
        )
        assertEquals(emptyList<String>(), offered().filter { it in CAPTURES || it == "ui.Screen" })

        open(
            """
            "*Screen.kt".file().template {
                captureValue("${'$'}x<caret>")
            }
            """,
        )
        assertEquals(emptyList<String>(), offered().filter { it in CAPTURES })

        open(
            """
            "*Screen.kt".file().template {
                captureValue(${"\"\"\""}<caret>${"\"\"\""})
            }
            """,
        )
        assertEquals(emptyList<String>(), offered().filter { it in CAPTURES })
    }

    fun `test templateSet の template では全テンプレートの指定が出て Role 名でも当たる`() {
        open(
            """
            "NewFeature".templateSet {
                template("<caret>")
            }
            """,
        )
        // The template set's row (feature.NewFeature) is in the list but is no member.
        assertEquals(listOf("data.Repository.a", "data.Repository.b", "feature.FeatureViewModel", "ui.Screen"), offered().sorted())

        open(
            """
            "NewFeature".templateSet {
                template("FeatureV<caret>")
            }
            """,
        )
        offered()
        myFixture.checkResult(AuthoringFixtures.inFunction("\"NewFeature\".templateSet {\n    template(\"feature.FeatureViewModel\")\n}"))
    }

    fun `test templateSet のメンバの arg ではそのテンプレートのパラメータと分岐のパラメータと capture が出る`() {
        open(
            """
            "NewFeature".templateSet {
                template("ui.Screen") { arg("<caret>", "x") }
            }
            """,
        )

        assertEquals(listOf("feature", "name", "nameOf", "title", "withState"), offered().sorted())
    }

    fun `test 定義の行がずれた後でも 一番近い template の capture を出す`() {
        // The definition says line 2; the file now has the call on line 4.
        open(
            """


            "*Screen.kt".file().template {
                captureValue("<caret>")
            }
            """,
        )

        assertEquals(listOf("feature", "name", "nameOf"), offered())
    }

    fun `test 定義がまだ読まれていなければ何も出さず 読み込みを1度頼む`() {
        service.emit(DefinitionState.NotLoaded)
        open(
            """
            "*Screen.kt".file().template {
                captureValue("<caret>")
            }
            """,
        )

        assertEquals(emptyList<String>(), offered())
        assertTrue(service.ensureLoadedCalls >= 1)
    }

    fun `test indexing 中でも候補が出る`() {
        open(
            """
            "*Screen.kt".file().template {
                captureValue("<caret>")
            }
            """,
        )

        val names = DumbModeTestUtils.computeInDumbModeSynchronously(project) { offered() }

        assertEquals(listOf("feature", "name", "nameOf"), names)
    }

    fun `test 引数の arg の候補は group を省いた指定でも出る`() {
        open(
            """
            "NewFeature".templateSet {
                template("FeatureViewModel") { arg("<caret>", "x") }
            }
            """,
        )
        assertEquals(listOf("feature", "layer", "title", "withState"), offered().sorted())

        open(
            """
            "NewFeature".templateSet {
                template("Screen") { arg("<caret>", "x") }
            }
            """,
        )
        assertEquals(listOf("feature", "name", "nameOf", "title", "withState"), offered().sorted())

        // Two templates of the role: the role alone says nothing, with the id it does.
        open(
            """
            "NewFeature".templateSet {
                template("Repository") { arg("<caret>", "x") }
            }
            """,
        )
        assertEquals(emptyList<String>(), offered().filter { it in REPOSITORY_ONLY })
        open(
            """
            "NewFeature".templateSet {
                template("Repository.b") { arg("<caret>", "x") }
            }
            """,
        )
        assertEquals(listOf("bOnly", "title"), offered().sorted())
    }

    fun `test 組の指定は template の候補にも arg の候補にも出ない`() {
        open(
            """
            "NewFeature".templateSet {
                template("<caret>")
            }
            """,
        )
        assertTrue(offered().none { it.contains("NewFeature") })

        open(
            """
            "NewFeature".templateSet {
                template("feature.NewFeature") { arg("<caret>", "x") }
            }
            """,
        )
        assertEquals(emptyList<String>(), offered().filter { it == "setParameter" })
    }

    fun `test 最も近い行の template を選び capture の tail にそのテンプレートの指定が出る`() {
        // The definition says lines 2 (screen), 6 (data.Repository.a) and 10 (data.Repository.b); the file is now 1 and 2 lines further down.
        service.emit(
            DefinitionState.Ready(
                listOf(AuthoringFixtures.snapshotWithSites(AuthoringFixtures.screen to 2, AuthoringFixtures.repositoryA to 6, AuthoringFixtures.repositoryB to 10)),
                Freshness.Fresh,
            ),
        )
        val blank = "\n".repeat(4)
        open(
            """
            "a".file().template { }$blank
            "b".file().template {
                captureValue("<caret>")
            }
            """,
        )
        for (line in listOf(2, 6, 10)) {
            service.resolveWith(DeclarationSiteRef("ScreenRole.kt", line), ResolvedSite.Found(myFixture.file.virtualFile, line))
        }

        myFixture.completeBasic()
        val shown = shownItems()

        assertEquals(setOf("aOnly"), shown.keys - "ScreenRole.kt")
        assertEquals("path capture" to " (data.Repository.a)", shown["aOnly"])

        val blankMore = "\n".repeat(9)
        open(
            """
            "a".file().template { }$blankMore
            "b".file().template {
                captureValue("<caret>")
            }
            """,
        )
        myFixture.completeBasic()
        val second = shownItems()

        assertEquals(setOf("bOnly"), second.keys - "ScreenRole.kt")
        assertEquals("path capture" to " (data.Repository.b)", second["bOnly"])
    }

    fun `test 候補は Kotlin やプラットフォームの候補より上に並び 後者も消えない`() {
        myFixture.addFileToProject("Other.kt", "")
        myFixture.addFileToProject("Another.kt", "")
        open(
            """
            "*Screen.kt".file().template {
                captureValue("<caret>")
            }
            """,
        )

        myFixture.completeBasic()
        val all = myFixture.lookupElementStrings.orEmpty()

        assertEquals(listOf("feature", "name", "nameOf"), all.take(3))
        assertTrue("the platform's own candidates stay: $all", "ScreenRole.kt" in all)
    }

    fun `test 演算子 の連鎖と ktFile の形でも capture が出る`() {
        open(
            """
            "feature" / capture("feature") / "${'$'}{capture("name")}Screen".ktFile().template {
                captureValue("<caret>")
            }
            """,
        )

        assertEquals(listOf("feature", "name", "nameOf"), offered())
    }

    fun `test テンプレートの中身の文字列に埋めた captureValue でも補完できる`() {
        open(
            """
            "*Screen.kt".file().template {
                "// ${'$'}{captureValue("<caret>")}Screen"
            }
            """,
        )
        assertEquals(listOf("feature", "name", "nameOf"), offered())

        open(
            """
            "*Screen.kt".file().template {
                ${"\"\"\""}
                // ${'$'}{captureValue("<caret>")}Screen
                ${"\"\"\""}
            }
            """,
        )
        assertEquals(listOf("feature", "name", "nameOf"), offered())
    }

    fun `test arg の第 1 引数でない文字列では候補が出ない`() {
        open(
            """
            "NewFeature".templateSet {
                template("ui.Screen") { arg("title", "<caret>") }
            }
            """,
        )

        assertEquals(emptyList<String>(), offered().filter { it in ARG_NAMES })
    }

    fun `test 開き引用符の前で補完して Kotlin の候補を選んでも文字列は残る`() {
        open(
            """
            "*Screen.kt".file().template {
                val valueName = "v"
                val valueOther = "w"
                captureValue(<caret>"x")
            }
            """,
        )

        myFixture.completeBasic()
        myFixture.lookup.currentItem = myFixture.lookup.items.first { it.lookupString == "valueName" }
        myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)

        myFixture.checkResult(
            AuthoringFixtures.inFunction(
                "\"*Screen.kt\".file().template {\n    val valueName = \"v\"\n    val valueOther = \"w\"\n    captureValue(valueName\"x\")\n}",
            ),
        )
    }

    fun `test 閉じていない文字列でも 選ぶと中身は切れずに名前に置き換わる`() {
        open(
            """
            "*Screen.kt".file().template {
                captureValue("na<caret>
            }
            """,
        )

        assertEquals(listOf("name", "nameOf"), offered())
        myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)

        myFixture.checkResult(AuthoringFixtures.inFunction("\"*Screen.kt\".file().template {\n    captureValue(\"name\n}"))
    }

    /** The type text and the tail text of each item the popup shows, by lookup string. */
    private fun shownItems(): Map<String, Pair<String?, String?>> = myFixture.lookup.items.associate { item ->
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        item.renderElement(presentation)
        item.lookupString to (presentation.typeText to presentation.tailText)
    }

    private companion object {
        val CAPTURES = setOf("feature", "name", "nameOf")
        val REPOSITORY_ONLY = setOf("aOnly", "bOnly")
        val ARG_NAMES = setOf("feature", "layer", "name", "nameOf", "title", "withState")
    }
}
