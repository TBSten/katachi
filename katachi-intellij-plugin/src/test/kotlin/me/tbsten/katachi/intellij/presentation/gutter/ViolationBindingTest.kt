package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.SarifLocation
import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Paths

/** Which violations the source file draws and where, the colour, and the hover text (design 2.5). */
class ViolationBindingTest {
    private val path = Paths.get("/project/arch/src/main/kotlin/com/example/service/HealthService.kt")

    @Test
    fun `宣言がある UnsatisfiedFileConstraint は 宣言の名前に付き 行はどの宣言か選ぶ手がかり`() {
        val target = ViolationBinding.targetOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 7))
        assertEquals(AnnotationTarget.AtDeclaration("HealthService", 7), target)
    }

    @Test
    fun `宣言があり 行が無い UnsatisfiedFileConstraint も宣言の名前に付く`() {
        val target = ViolationBinding.targetOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = null))
        assertEquals(AnnotationTarget.AtDeclaration("HealthService", null), target)
    }

    @Test
    fun `宣言が無く 行がある UnsatisfiedFileConstraint は その行`() {
        assertEquals(AnnotationTarget.AtLine(12), ViolationBinding.targetOf(AnnotationFixtures.unsatisfied(path, declaration = null, line = 12)))
    }

    @Test
    fun `行も宣言も無い UnsatisfiedFileConstraint と UncheckedFile は ファイルの上に出す`() {
        assertEquals(AnnotationTarget.FileLevel, ViolationBinding.targetOf(AnnotationFixtures.unsatisfied(path)))
        assertEquals(AnnotationTarget.FileLevel, ViolationBinding.targetOf(AnnotationFixtures.unchecked(path)))
    }

    @Test
    fun `足りない宣言は declaration を持つが 行が無く Missing の行があるので ファイルの上に出す`() {
        assertEquals(AnnotationTarget.FileLevel, ViolationBinding.targetOf(AnnotationFixtures.missing(path)))
        assertEquals(AnnotationTarget.FileLevel, ViolationBinding.targetOf(AnnotationFixtures.missing(path, found = "private val x (line 4)")))
        assertEquals(true, ViolationBinding.isMissingDeclaration(AnnotationFixtures.missing(path)))
        assertEquals("Missing: class DefaultGetUserUseCase : GetUserUseCase", ViolationBinding.missingLineOf(AnnotationFixtures.missing(path)))
    }

    @Test
    fun `行のある違反は Missing の行があっても足りない宣言として扱わない`() {
        val rejected = AnnotationFixtures.unsatisfied(path, "HealthService", line = 7, message = "Role: r / FileConstraint: \"c\"\nMissing: something a custom constraint said")
        assertEquals(false, ViolationBinding.isMissingDeclaration(rejected))
        assertEquals(AnnotationTarget.AtDeclaration("HealthService", 7), ViolationBinding.targetOf(rejected))
    }

    @Test
    fun `UnexpectedFile は ここでは描かない`() {
        assertEquals(AnnotationTarget.Skipped, ViolationBinding.targetOf(AnnotationFixtures.unexpected(path)))
    }

    @Test
    fun `定義側の違反は 報告された行に描き 行が無ければ描かない`() {
        val onLine = AnnotationFixtures.missingFile(path, line = 16)
        for (label in ViolationBinding.DEFINITION_SIDE) {
            val violation = onLine.copy(label = label, ruleId = "katachi.$label")
            assertEquals(label, AnnotationTarget.AtLine(16), ViolationBinding.targetOf(violation))
            assertEquals(label, AnnotationTarget.Skipped, ViolationBinding.targetOf(violation.copy(location = SarifLocation(path, null))))
        }
    }

    @Test
    fun `規則の場所は 関連する場所を先に 無ければ declaredAt を FileName と行に分ける`() {
        val related = SarifLocation(Paths.get("/project/arch/src/test/kotlin/ServiceRole.kt"), 94)
        assertEquals(RuleSite.At(related), ViolationBinding.ruleSiteOf(AnnotationFixtures.unsatisfied(path, "A", 7, related = listOf(related))))
        assertEquals(
            RuleSite.Declared(DeclarationSiteRef("ServiceRole.kt", 94)),
            ViolationBinding.ruleSiteOf(AnnotationFixtures.unsatisfied(path, "A", 7)),
        )
        assertNull(ViolationBinding.ruleSiteOf(AnnotationFixtures.unchecked(path)))
        assertNull(ViolationBinding.ruleSiteOf(AnnotationFixtures.unsatisfied(path, "A", 7, declaredAt = "ServiceRole.kt")))
    }

    @Test
    fun `色は SARIF の level どおりで 古い検査は 1 段弱くなる`() {
        val fresh = SarifLevel.entries.map { ViolationAnnotations.severityOf(it, stale = false) }
        val stale = SarifLevel.entries.map { ViolationAnnotations.severityOf(it, stale = true) }
        assertEquals(listOf(AnnotationSeverity.Error, AnnotationSeverity.Warning, AnnotationSeverity.WeakWarning, AnnotationSeverity.Information), fresh)
        assertEquals(listOf(AnnotationSeverity.Warning, AnnotationSeverity.WeakWarning, AnnotationSeverity.WeakWarning, AnnotationSeverity.Information), stale)
    }

    @Test
    fun `宣言のホバーは 規則 Role 宣言 規則の場所を言い 次の行に報告のメッセージが続く`() {
        val violation = AnnotationFixtures.unsatisfied(path, "HealthService", line = 7)
        val target = ViolationBinding.targetOf(violation)
        assertEquals(
            "katachi: \"Must be public\" (domain.Service) rejected HealthService — declared at ServiceRole.kt:94\n" + violation.message,
            ViolationAnnotations.hoverOf(violation, target, stale = false),
        )
    }

    @Test
    fun `宣言も足りない宣言でも無い違反のホバーは 種類と メッセージの 1 行目だけ 足りない宣言は Missing の行と規則の場所`() {
        val missing = AnnotationFixtures.missing(path, declaredAt = "ServiceRole.kt:94")
        assertEquals(
            "katachi: [UnsatisfiedFileConstraint] Missing: class DefaultGetUserUseCase : GetUserUseCase (ServiceRole.kt:94)",
            ViolationAnnotations.hoverOf(missing, ViolationBinding.targetOf(missing), stale = false),
        )
        val violation = AnnotationFixtures.unsatisfied(path)
        assertEquals(
            "katachi: [UnsatisfiedFileConstraint] Role: domain.Service / FileConstraint: \"Must be public\"",
            ViolationAnnotations.hoverOf(violation, ViolationBinding.targetOf(violation), stale = false),
        )
        val unchecked = AnnotationFixtures.unchecked(path)
        assertEquals(
            "katachi: [UncheckedFile] Katachi failed while checking this file, so nothing is known about it.",
            ViolationAnnotations.hoverOf(unchecked, AnnotationTarget.FileLevel, stale = false),
        )
    }

    @Test
    fun `古い検査のホバーは 先頭に stale と付く`() {
        val violation = AnnotationFixtures.missing(path, declaredAt = "ServiceRole.kt:94")
        assertEquals(
            "(stale) katachi: [UnsatisfiedFileConstraint] Missing: class DefaultGetUserUseCase : GetUserUseCase (ServiceRole.kt:94)",
            ViolationAnnotations.hoverOf(violation, AnnotationTarget.FileLevel, stale = true),
        )
    }

    @Test
    fun `報告に足りない項目があっても ホバーは読める文になる`() {
        val bare = AnnotationFixtures.unsatisfied(path, "A", 3).let { it.copy(properties = mapOf("declaration" to "A")) }
        assertEquals(
            "katachi: rejected A\n" + bare.message,
            ViolationAnnotations.hoverOf(bare, AnnotationTarget.AtDeclaration("A", 3), stale = false),
        )
    }

    @Test
    fun `規則へ移動の見出しは ファイル名と行`() {
        assertEquals("ServiceRole.kt:94", ViolationAnnotations.ruleLabelOf(RuleSite.Declared(DeclarationSiteRef("ServiceRole.kt", 94))))
        assertEquals("ServiceRole.kt:94", ViolationAnnotations.ruleLabelOf(RuleSite.At(SarifLocation(Paths.get("/p/ServiceRole.kt"), 94))))
        assertEquals("ServiceRole.kt", ViolationAnnotations.ruleLabelOf(RuleSite.At(SarifLocation(Paths.get("/p/ServiceRole.kt"), null))))
    }
}
