package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import me.tbsten.katachi.intellij.testing.KtFileContentGolden
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The annotations against the SARIF a failing `ktFileContent { }` really writes (the golden of katachi-konsist): which
 * result goes where, the hover, and that the fixtures the other tests use are that SARIF's shape and not a guess.
 * A missing declaration has a `declaration` and no line, so a test that only had rejected declarations could not tell them apart.
 */
class KtFileContentGoldenBindingTest {
    private val path = Paths.get("/project/arch/core/domain/GetUserUseCase.kt")

    @Test
    fun `実際の SARIF では 余計な宣言と形違いの宣言は宣言の名前に付き 足りない宣言は import もメンバもファイルの上に出る`() {
        val targets = KtFileContentGolden.results.associate { it.properties.getValue("declaration") to ViolationBinding.targetOf(it) }
        assertEquals(
            mapOf(
                "GetUserUseCaseImpl" to AnnotationTarget.AtDeclaration("GetUserUseCaseImpl", 5),
                "DefaultGetUserUseCase" to AnnotationTarget.FileLevel,
                "HomeViewModel" to AnnotationTarget.AtDeclaration("HomeViewModel", 3),
                "DefaultHomeViewModel" to AnnotationTarget.FileLevel,
                "CounterViewModel.count" to AnnotationTarget.FileLevel,
            ),
            targets,
        )
    }

    @Test
    fun `足りない宣言のホバーは Missing の行と 規則を書いた場所で Role の行ではない`() {
        val missing = KtFileContentGolden.byDeclaration("DefaultGetUserUseCase")
        val declaredAt = missing.properties.getValue("declaredAt")
        assertEquals(
            "katachi: [UnsatisfiedFileConstraint] Missing: class DefaultGetUserUseCase : GetUserUseCase ($declaredAt)",
            ViolationAnnotations.hoverOf(missing, ViolationBinding.targetOf(missing), stale = false),
        )
        val member = KtFileContentGolden.byDeclaration("CounterViewModel.count")
        assertEquals(
            "katachi: [UnsatisfiedFileConstraint] Missing: public val count: Int in class CounterViewModel (${member.properties.getValue("declaredAt")})",
            ViolationAnnotations.hoverOf(member, ViolationBinding.targetOf(member), stale = true).removePrefix(ViolationAnnotations.STALE),
        )
    }

    @Test
    fun `足りない宣言のホバーは 「rejected」とは言わず 古い検査には stale が付く`() {
        val missing = KtFileContentGolden.byDeclaration("DefaultHomeViewModel")
        val hover = ViolationAnnotations.hoverOf(missing, ViolationBinding.targetOf(missing), stale = true)
        assertEquals(true, hover.startsWith("(stale) katachi: [UnsatisfiedFileConstraint] Missing: class DefaultHomeViewModel : HomeViewModel ("))
        assertEquals(false, "rejected" in hover)
    }

    @Test
    fun `余計な宣言のホバーは 制約と Role と宣言元を名指しして 続けて報告の本文`() {
        val rejected = KtFileContentGolden.byDeclaration("GetUserUseCaseImpl")
        val hover = ViolationAnnotations.hoverOf(rejected, ViolationBinding.targetOf(rejected), stale = false)
        assertEquals(
            "katachi: \"ktFileContent\" (Screen) rejected GetUserUseCaseImpl — declared at ${rejected.properties.getValue("declaredAt")}\n${rejected.message.trim()}",
            hover,
        )
    }

    @Test
    fun `テストの fixtures は 実際の SARIF と同じ message と properties になっている`() {
        val missing = KtFileContentGolden.byDeclaration("DefaultGetUserUseCase")
        val fixture = AnnotationFixtures.missing(
            path,
            declaration = "DefaultGetUserUseCase",
            requires = "class DefaultGetUserUseCase : GetUserUseCase",
            constraint = "ktFileContent",
            role = "Screen",
            declaredAt = missing.properties.getValue("declaredAt"),
        )
        assertEquals(missing.message, fixture.message)
        assertEquals(missing.properties - "path", fixture.properties - "path")
        assertEquals(missing.location?.startLine, fixture.location?.startLine)

        val found = KtFileContentGolden.byDeclaration("CounterViewModel.count")
        assertEquals(
            found.message.lines().take(4),
            AnnotationFixtures.missing(
                path, "CounterViewModel.count", "public val count: Int in class CounterViewModel", found = "private val count (line 4)",
                constraint = "ktFileContent", role = "Screen", declaredAt = found.properties.getValue("declaredAt"),
            ).message.lines().take(4),
        )
    }

    @Test
    fun `contract の複製は katachi-konsist の golden と同じ`() {
        val golden = Paths.get("..", "katachi-konsist", "src", "test", "resources", "sarif", "ktfilecontent.sarif")
        assumeTrue("katachi-konsist's golden is not next to the plugin", Files.isRegularFile(golden))
        assertEquals(Files.readString(golden), DefinitionFixtures.sarifText("katachi-ktfilecontent-sarif"))
    }
}
