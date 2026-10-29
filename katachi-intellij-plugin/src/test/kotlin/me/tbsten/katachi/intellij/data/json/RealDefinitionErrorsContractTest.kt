package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionModel
import me.tbsten.katachi.intellij.model.DefinitionProblemKind
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionErrorTargets
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionMark
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionMarkSource
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

/**
 * C-1 of ide-authoring over the real thing: `sample/jvm` broken in three ways and run with the real
 * `:architecture-test:katachiInternalDefinitionJson`, the output left unedited under `contract/authoring/`:
 *
 * - `real-jvm-wildcard-errors.json`: `ServiceRole.kt`'s `"${capture("name")}Service"` written as `"*Service"` (a `.template { }` on an unnamed `*`)
 * - `real-jvm-duplicate-errors.json`: `domainGroup()` called twice in `ProjectArchitecture.kt`
 * - `real-jvm-typo-definition.json`: `captureValue("name")` written as `captureValue("nmae")` (the preview fails, so it is a `problems[]` entry)
 *
 * Taking them again: copy `sample/jvm` out of the checkout, point its `includeBuild` and version catalog at the checkout,
 * `git init` + `git add -A`, break it as above, and run the task with a project cache of its own per copy. The editor's mark
 * must land on the line the file really has at the line katachi names.
 */
class RealDefinitionErrorsContractTest {
    private val moduleId = module(":arch").id

    private fun sources(): List<Path> {
        val root = Paths.get(javaClass.getResource("/contract/gutter/sources/jvm")?.toURI() ?: error("no sources for jvm"))
        return Files.walk(root).use { stream -> stream.filter { it.name.endsWith(".kt") }.toList() }
    }

    private fun linesOf(fileName: String): List<String> = Files.readAllLines(sources().single { it.name == fileName })

    private fun marksOf(errors: DefinitionErrors, definition: DefinitionModel? = null): List<DefinitionMark> {
        val snapshots = definition?.let { listOf(definitionSnapshot(it, module(":arch"))) }.orEmpty()
        return DefinitionErrorTargets.marksOf(
            errors,
            snapshots,
            definitionStale = false,
            lineOf = { _, site -> site.line.takeIf { it <= linesOf(site.fileName).size } },
            lineCount = 10_000,
        )
    }

    private fun errorsOf(fixture: String): DefinitionErrors {
        val parsed = parseDefinitionErrorsJson(ContractFixtures.text("authoring/$fixture.json"), Paths.get("/fixture/$fixture.json"), moduleId)
            ?: throw AssertionError("$fixture was not read")
        return DefinitionErrors(parsed)
    }

    @Test
    fun `名前の無い星に template を付けた誤りは ServiceRole kt の template の行に ERROR が付く`() {
        val errors = errorsOf("real-jvm-wildcard-errors")

        assertEquals(listOf("me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException"), errors.errors.map { it.exceptionClassName })
        assertEquals(DeclarationSiteRef("ServiceRole.kt", 59), errors.errors.single().site)
        val mark = marksOf(errors).single()
        assertEquals(59, mark.line)
        assertEquals(DefinitionMarkSource.Error, mark.source)
        assertTrue(".template" in linesOf("ServiceRole.kt")[mark.line - 1])
    }

    @Test
    fun `group の重複の誤りは重複した group を宣言した DomainGroup kt の行に付く`() {
        val errors = errorsOf("real-jvm-duplicate-errors")

        assertEquals(listOf("me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException"), errors.errors.map { it.exceptionClassName })
        assertEquals(DeclarationSiteRef("DomainGroup.kt", 9), errors.errors.single().site)
        val mark = marksOf(errors).single()
        assertEquals(9, mark.line)
        assertTrue(".group" in linesOf("DomainGroup.kt")[mark.line - 1])
    }

    @Test
    fun `captureValue の typo は problems の TemplatePreviewFailed になり captureValue を呼んだ行に WARNING が付く`() {
        val definition = parseDefinitionJson(ContractFixtures.text("authoring/real-jvm-typo-definition.json"), Paths.get("/fixture/typo.json")).definition
        val kinds = definition.problems.map { it.kind }

        assertEquals(listOf(DefinitionProblemKind.TemplatePreviewFailed, DefinitionProblemKind.TemplateSetPreviewFailed), kinds)
        assertEquals(setOf("me.tbsten.katachi.dsl.KatachiUnknownTemplateCaptureException"), definition.problems.map { it.exception }.toSet())
        val marks = marksOf(DefinitionErrors.None, definition)
        assertEquals(listOf(60, 60), marks.map { it.line })
        assertEquals(setOf(DefinitionMarkSource.Problem), marks.map { it.source }.toSet())
        assertTrue("captureValue" in linesOf("ServiceRole.kt")[59])
    }
}
