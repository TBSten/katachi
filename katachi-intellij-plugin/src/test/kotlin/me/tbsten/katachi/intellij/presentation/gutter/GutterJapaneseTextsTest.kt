package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DeclarationModel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.testing.ALL_ACTIONS
import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.GutterFixtures
import me.tbsten.katachi.intellij.testing.declarationModel
import me.tbsten.katachi.intellij.testing.englishGutter
import me.tbsten.katachi.intellij.testing.fileDeclarations
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.japaneseGutter
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.markOf
import me.tbsten.katachi.intellij.testing.menuInputOf
import me.tbsten.katachi.intellij.testing.roleFileDeclarations
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.shown
import me.tbsten.katachi.intellij.testing.templateRef
import me.tbsten.katachi.intellij.testing.withTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/**
 * The gutter of a source file under the Japanese language pack: the ◆'s tooltip, its menu and the hover of a violation say their
 * own words in Japanese, and quote what katachi wrote (a rule, the report's message) as it is. The English texts are the ones the
 * other tests of this package compare with.
 */
class GutterJapaneseTextsTest {
    private val path = GutterFixtures.SERVICE_PATH
    private val legacy = "src/main/kotlin/com/example/service/LegacyStatusService.kt"
    private val service: RoleModel = roleModel("domain.Service", layoutEntry(path, matchedFiles = listOf(path, legacy)))
        .withTemplates(templateRef(roleModel("domain.Service"), "service"))
    private val healthService = SourceDeclaration(SourceDeclarationKind.Class, "HealthService", 7)
    private val allowed = declarationModel(SourceDeclarationKind.Class, "HealthService", 7, rules = listOf(GutterFixtures.SERVICE_RULE))

    private fun mark(declaration: DeclarationModel = allowed, stale: Boolean = false, missing: List<RuleRef> = emptyList(), role: RoleModel = service): Pair<GutterMark, MenuInput> {
        val roles = fileRoles(path, role)
        val explanation = roleFileDeclarations(role, fileDeclarations(path, declaration, missing = missing))
            .let { it.copy(constraint = it.constraint.copy(declaredAt = DeclarationSiteRef("ServiceRole.kt", 73))) }
        val mark = markOf(roles, listOf(healthService), listOf(explanation), stale)
        return mark to menuInputOf(mark, roles)
    }

    private fun tooltip(declaration: DeclarationModel = allowed, stale: Boolean = false, strings: GutterStrings): String {
        val roles = fileRoles(path, service)
        val explanation = roleFileDeclarations(service, fileDeclarations(path, declaration))
        return GutterMarks.of(GutterInput(roles, listOf(explanation), listOf(healthService), SourceFileKind.Kotlin, stale), strings).single().tooltip
    }

    @Test
    fun `日本語の ◆ のツールチップは 許可した行を引いたまま 許可と古さを日本語で言う`() {
        assertEquals("katachi: domain.Service — class HealthService (ServiceRole.kt:80) で許可", tooltip(strings = japaneseGutter))
        assertEquals("katachi: domain.Service — class HealthService (ServiceRole.kt:80) で許可（変更の前の結果）", tooltip(stale = true, strings = japaneseGutter))
        assertEquals("katachi: domain.Service — allowed by class HealthService (ServiceRole.kt:80) (stale)", tooltip(stale = true, strings = englishGutter))
    }

    @Test
    fun `日本語の ◆ のツールチップは 禁止 形の違い どの行も許さない 新しい判定 を日本語で言う`() {
        val prohibited = declarationModel(SourceDeclarationKind.Class, "HealthService", 7, DeclarationVerdict.Prohibited, rules = listOf(RuleRef("not.class *Cache", null)))
        val mismatched = declarationModel(SourceDeclarationKind.Class, "HealthService", 7, DeclarationVerdict.Mismatched, rules = listOf(GutterFixtures.SERVICE_RULE), difference = "must be public")
        val unexpected = declarationModel(SourceDeclarationKind.Class, "HealthService", 7, DeclarationVerdict.Unexpected)
        val unknown = declarationModel(SourceDeclarationKind.Class, "HealthService", 7, DeclarationVerdict.Unknown("Waived"), rules = emptyList())
        assertEquals("katachi: domain.Service — not.class *Cache で禁止", tooltip(prohibited, strings = japaneseGutter))
        assertEquals("katachi: domain.Service — class HealthService (ServiceRole.kt:80) と合いません: must be public", tooltip(mismatched, strings = japaneseGutter))
        assertEquals("katachi: domain.Service — ktFileContent のどの行も許可していません（検査では UnsatisfiedFileConstraint）", tooltip(unexpected, strings = japaneseGutter))
        assertEquals("katachi: domain.Service — 新しい katachi の判定: Waived", tooltip(unknown, strings = japaneseGutter))
        assertEquals("katachi: domain.Service — judged Waived by a newer katachi", tooltip(unknown, strings = englishGutter))
    }

    @Test
    fun `定義を読み直せなかった ◆ の 2 行目は 日本語で理由を言い Gradle の行はそのまま引く`() {
        val roles = fileRoles("a/A.kt", roleModel("domain.Service", layoutEntry("a/A.kt")), freshness = Freshness.Stale(DEFINITION_INSTANT, StaleReason.ReloadFailed(LoadFailure.Gradle(GradleFailure.CompilationFailed(emptyList())))))
        val marks = GutterMarks.of(GutterInput(roles, emptyList(), emptyList(), SourceFileKind.Other, stale = true), japaneseGutter)
        assertEquals("katachi: domain.Service\n定義を読み直せませんでした: 定義がコンパイルできません", marks.single().tooltip)
        val other = GutterTooltips.summary(LoadFailure.Gradle(GradleFailure.Other(listOf("Could not connect to the daemon"))), japaneseGutter)
        assertEquals("Could not connect to the daemon", other)
        assertEquals("Gradle の実行に失敗しました", GutterTooltips.summary(LoadFailure.Gradle(GradleFailure.Other(emptyList())), japaneseGutter))
    }

    @Test
    fun `読み直しの失敗の理由は 日本語ではどれも日本語の 1 行になる`() {
        val json = Paths.get("/w/definition.json")
        val failures = listOf(
            LoadFailure.Gradle(GradleFailure.CompilationFailed(emptyList())),
            LoadFailure.Gradle(GradleFailure.TaskNotFound(":arch:katachiInternalDefinitionJson", emptyList())),
            LoadFailure.Gradle(GradleFailure.ArchitectureNotSet(emptyList())),
            LoadFailure.Gradle(GradleFailure.ProcessorRejected("IllegalArgumentException", listOf("unknown arg"))),
            LoadFailure.JsonMissing(json),
            LoadFailure.MalformedJson(json, "EOF"),
            LoadFailure.IncompatibleJson(json, "roles[0]", "missing key"),
            LoadFailure.Cancelled,
        )
        for (failure in failures) {
            val summary = GutterTooltips.summary(failure, japaneseGutter)
            assertTrue("$failure: $summary", JAPANESE.containsMatchIn(summary))
            assertFalse("$failure: $summary", summary.contains('\n'))
        }
        assertEquals("katachi { architecture } が設定されていません", GutterTooltips.summary(failures[2], japaneseGutter))
    }

    @Test
    fun `日本語の ◆ のメニューは 行の並びと飛び先が英語と同じで 文言だけが日本語`() {
        val (_, input) = mark()
        val japanese = MenuModel.of(input, ALL_ACTIONS, japaneseGutter)
        val english = MenuModel.of(input, ALL_ACTIONS, englishGutter)
        assertEquals(
            listOf(
                "◆ class HealthService で許可 | ServiceRole.kt:80",
                "◇ Role domain.Service | ServiceRole.kt:1",
                "== domain.Service のほかのファイル（1）",
                "LegacyStatusService.kt | src/main/kotlin/com/example/service",
                "-----",
                "+ テンプレートから新しい domain.Service を作る…",
                "▶ 検査を実行（:arch:test）",
                "⟳ 定義を読み直す",
            ),
            japanese.shown(),
        )
        assertEquals(english.rows().map { it.target to it.enabled }, japanese.rows().map { it.target to it.enabled })
        assertEquals(english.title, japanese.title)
    }

    @Test
    fun `日本語のメニューは 足りない宣言 古さ 検査中 見つからない宣言元 も日本語で言う`() {
        val missing = listOf(RuleRef("class DefaultGetUser", DeclarationSiteRef("ServiceRole.kt", 82)))
        val (_, staleInput) = mark(stale = true, missing = missing)
        val stale = MenuModel.of(staleInput, ALL_ACTIONS.copy(checkRunning = true), japaneseGutter).shown()
        assertEquals("⟳ 定義を読み直す", stale.first())
        assertTrue(stale.toString(), stale.containsAll(listOf("== このファイルに足りない宣言（1）", "== domain.Service のほかのファイル（1、変更の前の結果）", "(disabled) ▶ 検査中…")))

        val alone = roleModel("domain.Service", layoutEntry(path, matchedFiles = listOf(path)))
        val (_, aloneInput) = mark(role = alone, stale = true)
        val unresolved = aloneInput.copy(unresolved = mapOf(GutterFixtures.SERVICE_RULE.declaredAt!! to "no file of that name"))
        val shown = MenuModel.of(unresolved, ALL_ACTIONS, japaneseGutter).shown()
        assertEquals("(disabled) ◆ class HealthService で許可 | （宣言元を特定できません: no file of that name）", shown[1])
        assertTrue(shown.toString(), shown.containsAll(listOf("== domain.Service のほかのファイル（変更の前の結果）", "(disabled) — ほかのファイルはまだありません")))
    }

    @Test
    fun `日本語のメニューは どの行も許さない宣言と 判定がまだの宣言も日本語で言う`() {
        val unexpected = declarationModel(SourceDeclarationKind.Class, "HealthService", 7, DeclarationVerdict.Unexpected)
        val (_, input) = mark(unexpected)
        assertEquals("◆ 許可する行がありません — ktFileContent を開く | ServiceRole.kt:73", MenuModel.of(input, ALL_ACTIONS, japaneseGutter).shown().first())

        val roles = fileRoles(path, service)
        val notComputed = markOf(roles, listOf(healthService), listOf(roleFileDeclarations(service, fileDeclarations(path))))
        assertEquals("⟳ 定義を読み直して、何が許可しているかを見る", MenuModel.of(menuInputOf(notComputed, roles), ALL_ACTIONS, japaneseGutter).rows().first().text)
    }

    @Test
    fun `兄弟が多いときの残りの行は 日本語では ほか N ファイル と言い 数を区切らない`() {
        val files = (1..1250).map { "src/main/kotlin/com/example/service/S$it.kt" }
        val many = roleModel("domain.Service", layoutEntry(path, matchedFiles = listOf(path) + files))
        val (_, input) = mark(role = many)
        assertTrue(MenuModel.of(input, ALL_ACTIONS, japaneseGutter).shown().contains("(disabled) … ほか 1235 ファイル"))
        assertTrue(MenuModel.of(input, ALL_ACTIONS, englishGutter).shown().contains("(disabled) … and 1235 more"))
    }

    @Test
    fun `日本語の違反のホバーは 制約と Role と宣言元を日本語の文で言い 報告のメッセージはそのまま続ける`() {
        val file = Paths.get("/project/arch/src/main/kotlin/com/example/service/HealthService.kt")
        val violation = AnnotationFixtures.unsatisfied(file, "HealthService", line = 7)
        val target = ViolationBinding.targetOf(violation)
        assertEquals(
            "katachi: \"${AnnotationFixtures.CONSTRAINT}\" (domain.Service) が HealthService を拒否 — 宣言元: ServiceRole.kt:94\n" + violation.message,
            ViolationAnnotations.hoverOf(violation, target, stale = false, japaneseGutter),
        )
        val bare = violation.copy(properties = mapOf("declaration" to "HealthService"))
        assertEquals("katachi: HealthService は許可されていません\n" + bare.message, ViolationAnnotations.hoverOf(bare, target, stale = false, japaneseGutter))
        val missing = AnnotationFixtures.missing(file, declaredAt = "ServiceRole.kt:94")
        assertEquals(
            "（変更の前の結果）katachi: [UnsatisfiedFileConstraint] Missing: class DefaultGetUserUseCase : GetUserUseCase (ServiceRole.kt:94)",
            ViolationAnnotations.hoverOf(missing, AnnotationTarget.FileLevel, stale = true, japaneseGutter),
        )
    }

    @Test
    fun `日本語の文言はどれも日本語の文字を含み 英語の文言は日本語を含まず どちらも引数を埋め残さない`() {
        val exceptions = setOf("roleRow") // `◇ Role X`: Role is the DSL's word in both languages.
        for ((name, text) in textsOf(japaneseGutter)) {
            assertFalse("ja $name: $text", PLACEHOLDER.containsMatchIn(text))
            if (name !in exceptions) assertTrue("ja $name: $text", JAPANESE.containsMatchIn(text))
        }
        for ((name, text) in textsOf(englishGutter)) {
            assertFalse("en $name: $text", PLACEHOLDER.containsMatchIn(text) || JAPANESE.containsMatchIn(text))
        }
    }

    private fun textsOf(s: GutterStrings): List<Pair<String, String>> = listOf(
        "staleSuffix" to s.staleSuffix, "reloadFailed" to s.reloadFailed("x"), "failureCompilation" to s.failureCompilation,
        "failureTaskNotFound" to s.failureTaskNotFound, "failureArchitectureNotSet" to s.failureArchitectureNotSet,
        "failureProcessorRejected" to s.failureProcessorRejected, "failureDefinitionRejected" to s.failureDefinitionRejected,
        "failureGradle" to s.failureGradle, "failureJsonMissing" to s.failureJsonMissing, "failureJsonMalformed" to s.failureJsonMalformed,
        "failureJsonIncompatible" to s.failureJsonIncompatible, "failureCancelled" to s.failureCancelled, "notComputed" to s.notComputed,
        "allowedBy" to s.allowedBy("r"), "prohibitedBy" to s.prohibitedBy("r"), "mismatched" to s.mismatched("r"), "unexpected" to s.unexpected,
        "judgedByNewer" to s.judgedByNewer("v"), "unrecordedRule" to s.unrecordedRule, "menuReloadToSee" to s.menuReloadToSee,
        "menuAllowedBy" to s.menuAllowedBy("r"), "menuProhibitedBy" to s.menuProhibitedBy("r"), "menuMismatched" to s.menuMismatched("r"),
        "menuUnexpected" to s.menuUnexpected, "menuJudgedByNewer" to s.menuJudgedByNewer("v"), "missingSection" to s.missingSection(2),
        "declarationNotFound" to s.declarationNotFound("why"), "roleRow" to s.roleRow("a.B"),
        "siblingsSection" to s.siblingsSection("a.B", null, false), "siblingsSectionStale" to s.siblingsSection("a.B", null, true),
        "siblingsSectionCount" to s.siblingsSection("a.B", 3, false), "siblingsSectionCountStale" to s.siblingsSection("a.B", 3, true),
        "noSibling" to s.noSibling, "moreSiblings" to s.moreSiblings(4), "newFromTemplate" to s.newFromTemplate("a.B"), "checking" to s.checking,
        "runCheck" to s.runCheck(":a:test"), "reload" to s.reload, "stalePrefix" to s.stalePrefix, "rejected" to s.rejected("", "A"),
        "rejectedBy" to s.rejected("\"c\" (a.B)", "A"), "declaredAt" to s.declaredAt("A.kt:1"),
    )

    private companion object {
        val JAPANESE = Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff]")

        /** An argument left in a text: `{0}`. `katachi { architecture }` is the DSL, not one. */
        val PLACEHOLDER = Regex("\\{\\d")
    }
}
