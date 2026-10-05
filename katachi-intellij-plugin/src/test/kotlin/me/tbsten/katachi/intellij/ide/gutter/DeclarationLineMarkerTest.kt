package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.DumbService
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DumbModeTestUtils
import me.tbsten.katachi.intellij.model.DeclarationModel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.GutterFixtures
import me.tbsten.katachi.intellij.testing.declarationModel
import me.tbsten.katachi.intellij.testing.fileDeclarations
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleFileDeclarations
import me.tbsten.katachi.intellij.testing.roleModel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/** V2 of the gutter's P1: the ◆ the real daemon draws for what the fake definition says (design 2.1, 2.2, 2.6). */
internal class DeclarationLineMarkerTest : GutterTestBase() {
    private val service = roleModel("domain.Service", layoutEntry(GutterFixtures.SERVICE_PATH))
    private val handler = roleModel("api.Handler", layoutEntry(GutterFixtures.SERVICE_PATH))
    private val repository = roleModel("data.Repository", layoutEntry(GutterFixtures.REPOSITORY_PATH))
    private val serviceFile = "HealthService.kt"

    private fun allowedService(vararg extra: DeclarationModel) = listOf(
        roleFileDeclarations(
            service,
            fileDeclarations(
                GutterFixtures.SERVICE_PATH,
                declarationModel(SourceDeclarationKind.Class, "HealthService", 7, rules = listOf(GutterFixtures.SERVICE_RULE)),
                *extra,
            ),
        ),
    )

    fun `test 宣言を縛る Role のファイルでは 宣言の名前に許可した行を言う ◆ が付く`() {
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService())
        val marks = drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        assertEquals(
            listOf(DrawnMark(7, "HealthService", "katachi: domain.Service — allowed by class HealthService (ServiceRole.kt:80)", isLeaf = true, hasMenu = true)),
            marks,
        )
    }

    fun `test 宣言ごとに 許されない宣言と まだ計算していない宣言を言い分ける`() {
        val text = GutterFixtures.HEALTH_SERVICE_KT + "\n\nclass HealthCache\n\nfun helper() = 1\n"
        val unexpected = declarationModel(SourceDeclarationKind.Class, "HealthCache", 13, DeclarationVerdict.Unexpected)
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService(unexpected))
        val marks = drawn(serviceFile, text)
        assertEquals(listOf("HealthService", "HealthCache", "helper"), marks.map { it.leaf })
        assertEquals(
            listOf(
                "katachi: domain.Service — allowed by class HealthService (ServiceRole.kt:80)",
                "katachi: domain.Service — no line in ktFileContent allows this (UnsatisfiedFileConstraint in the check)",
                "katachi: domain.Service — not computed yet. Refresh the definition.",
            ),
            marks.map { it.tooltip },
        )
    }

    fun `test 宣言を縛らない Role のファイルには 最初の宣言に Role だけの ◆ が 1 つ`() {
        ready("HealthRepository.kt", fileRoles(GutterFixtures.REPOSITORY_PATH, repository))
        val marks = drawn("HealthRepository.kt", GutterFixtures.HEALTH_REPOSITORY_KT)
        assertEquals(listOf(DrawnMark(5, "HealthRepository", "katachi: data.Repository", isLeaf = true, hasMenu = true)), marks)
    }

    fun `test 宣言の無い kt ファイルでは package の行に 1 つ`() {
        ready("Empty.kt", fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService())
        val marks = drawn("Empty.kt", "// a comment\npackage com.example.service\n")
        assertEquals(listOf(2 to "package"), marks.map { it.line to it.leaf })
    }

    fun `test Role が 2 つなら ◆ は 1 つで 両方の名前を並べる`() {
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service, handler), allowedService())
        val marks = drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        assertEquals(listOf("katachi: domain.Service, api.Handler — allowed by class HealthService (ServiceRole.kt:80)"), marks.map { it.tooltip })
    }

    fun `test 当たらないファイル 答えの無いファイル katachi の無いプロジェクトには何も出さない`() {
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, outcome = MatchOutcome.Unexpected), allowedService())
        assertEquals(emptyList<DrawnMark>(), drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT))

        ready(serviceFile, null)
        assertEquals(emptyList<DrawnMark>(), redrawn())

        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService())
        hasDefinitionModule = false
        assertEquals(emptyList<DrawnMark>(), redrawn())
    }

    fun `test 定義が古いと許可元に stale を付けて出し続ける`() {
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService(), Freshness.Stale(DEFINITION_INSTANT, StaleReason.DefinitionChanged))
        assertEquals(
            listOf("katachi: domain.Service — allowed by class HealthService (ServiceRole.kt:80) (stale)"),
            drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT).map { it.tooltip },
        )
    }

    fun `test ファイルが定義より後に書かれたか 保存していない編集があると stale 保存しても更新するまで消えない`() {
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService())
        modifiedAt = DEFINITION_INSTANT.plusSeconds(1)
        assertTrue(drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT).single().tooltip!!.endsWith("(stale)"))

        modifiedAt = DEFINITION_INSTANT.minusSeconds(1)
        assertFalse(redrawn().single().tooltip!!.endsWith("(stale)"))

        // From here the world keeps the file's time: a save writes it after the definition was made.
        modifiedAt = null
        assertFalse(redrawn().single().tooltip!!.endsWith("(stale)"))
        typeAtStart("\n\n\n")
        val edited = redrawn().single()
        assertEquals("the ◆ follows its declaration down, before any refresh", 10 to "HealthService", edited.line to edited.leaf)
        assertTrue(edited.tooltip!!.endsWith("(stale)"))

        saveAll()
        assertTrue("saving writes the file after the definition: still stale", redrawn().single().tooltip!!.endsWith("(stale)"))

        refreshedSnapshot()
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService())
        assertFalse("a refresh makes a definition written after the file", redrawn().single().tooltip!!.endsWith("(stale)"))
    }

    fun `test 本物の fileStateOf は VFS の時刻を読み 定義より後に書かれたファイルを stale にする`() {
        useLocalFiles()
        realFileState = true
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService())
        open(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        val onDisk = lightPath(serviceFile)
        val file = myFixture.file.virtualFile

        fun writtenAt(time: Instant) {
            Files.setLastModifiedTime(onDisk, FileTime.from(time))
            file.refresh(false, false)
        }

        writtenAt(DEFINITION_INSTANT.minusSeconds(60))
        assertFalse("written before the definition", redrawn().single().tooltip!!.endsWith("(stale)"))
        writtenAt(DEFINITION_INSTANT.plusSeconds(60))
        assertTrue("written after the definition", redrawn().single().tooltip!!.endsWith("(stale)"))
    }

    fun `test 定義が古すぎて読めない状態では 前の答えが残っていても ◆ を出さない`() {
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService())
        assertEquals(1, drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT).size)
        val previous = definitions.state.value as DefinitionState.Ready
        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.KatachiTooOld, previous))
        assertEquals(emptyList<DrawnMark>(), redrawn())
        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.LoadFailed, previous))
        assertEquals("another failure keeps drawing the last answer", 1, redrawn().size)
    }

    fun `test 規則の文字の 型引数と アンパサンドは ツールチップで HTML として壊れず 読み上げの名前は元のまま`() {
        val rule = RuleRef("class Box<T> & List<String>", DeclarationSiteRef("ServiceRole.kt", 80))
        val declarations = listOf(
            roleFileDeclarations(
                service,
                fileDeclarations(GutterFixtures.SERVICE_PATH, declarationModel(SourceDeclarationKind.Class, "HealthService", 7, rules = listOf(rule))),
            ),
        )
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), declarations)
        myFixture.configureByText(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        val info = collectedInfos(myFixture.file).single()
        assertEquals(
            "katachi: domain.Service — allowed by class Box&lt;T&gt; &amp; List&lt;String&gt; (ServiceRole.kt:80)",
            info.lineMarkerTooltip,
        )
        assertEquals(
            "katachi: domain.Service — allowed by class Box<T> & List<String> (ServiceRole.kt:80)",
            info.createGutterRenderer()?.accessibleName,
        )
    }

    fun `test 定義ファイルの template に注入された Kotlin の断片には ◆ を出さない`() {
        val definitionFile = "Architecture.kt"
        myFixture.configureByText(definitionFile, "fun definitions() {\n    \"*UseCase\".ktFile().template {\n        \"interface SampleUseCase\"\n    }\n}\n")
        val manager = InjectedLanguageManager.getInstance(project)
        val host = PsiTreeUtil.findChildrenOfType(myFixture.file, KtStringTemplateExpression::class.java).first { it.text.contains("SampleUseCase") }
        val injected = manager.getInjectedPsiFiles(host).orEmpty().map { it.first.containingFile }.distinct().single()
        assertTrue(manager.isInjectedFragment(injected))
        // The definition answers for the fragment's path as well, so that only the provider's own check keeps it clear.
        ready(definitionFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService())
        definitions.answer(Paths.get(injected.viewProvider.virtualFile.path), fileRoles(GutterFixtures.SERVICE_PATH, service))

        assertEquals("the fragment has no ◆", emptyList<DrawnMark>(), collectedDirectly(injected))
        assertTrue("the same answer does draw on the host, so the test can tell", collectedDirectly(myFixture.file).isNotEmpty())
    }

    fun `test 答えが無くなったら 本物の HighlightingRefresher が開いているファイルの ◆ を消す`() {
        useLocalFiles()
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService())
        assertEquals(1, drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT).size)

        definitions.emit(DefinitionState.NotLoaded)

        assertEquals("the test does not restart the daemon: the refresher does", emptyList<DrawnMark>(), redrawnByRefresher())
    }

    fun `test Kotlin script にはファイル先頭に Role だけの ◆ が 1 つ`() {
        ready("build.gradle.kts", fileRoles("build.gradle.kts", service), allowedService())
        myFixture.configureByText("build.gradle.kts", "\nplugins {\n    kotlin(\"jvm\")\n}\nclass Local\n")
        // The light project cannot highlight a script (no script configuration; GutterSpikeTest): ask the provider itself.
        assertEquals(listOf(DrawnMark(2, "plugins", "katachi: domain.Service", isLeaf = true, hasMenu = true)), collectedDirectly(myFixture.file))
    }

    fun `test dumb mode でも同じ ◆ を出す`() {
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), allowedService())
        myFixture.configureByText(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        val smart = collectedDirectly(myFixture.file)
        assertTrue(DumbService.isDumbAware(provider))
        val dumb = DumbModeTestUtils.computeInDumbModeSynchronously(project) { collectedDirectly(myFixture.file) }
        assertEquals(1, smart.size)
        assertEquals(smart, dumb)
    }

    fun `test 状態の列を流すと 答えのある間は出し続け 古い答えには stale を付ける`() {
        definitions.answer(lightPath(serviceFile), fileRoles(GutterFixtures.SERVICE_PATH, service))
        definitions.explain(lightPath(serviceFile), allowedService())
        myFixture.configureByText(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        val states = DefinitionStateScripts.standardCycle(snapshot) +
            DefinitionState.Unavailable(DefinitionUnavailableReason.LoadFailed, DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        definitions.play(states) { state ->
            val marks = redrawn()
            val answer = when (state) {
                DefinitionState.NotLoaded -> null
                is DefinitionState.Loading -> state.previous
                is DefinitionState.Ready -> state
                is DefinitionState.Unavailable -> state.previous
            }
            if (answer == null) {
                assertEquals("$state", emptyList<DrawnMark>(), marks)
            } else {
                val stale = answer.freshness is Freshness.Stale || state is DefinitionState.Unavailable
                assertEquals("$state", 1, marks.size)
                assertEquals("$state", stale, marks.single().tooltip!!.endsWith("(stale)"))
            }
        }
    }
}
