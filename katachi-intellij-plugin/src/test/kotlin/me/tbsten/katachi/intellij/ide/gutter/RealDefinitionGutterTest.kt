package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.lang.Language
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import me.tbsten.katachi.intellij.data.sarif.parseSarifJson
import me.tbsten.katachi.intellij.ide.gutter.annotator.AnnotatorTestBase
import me.tbsten.katachi.intellij.ide.gutter.annotator.DrawnAnnotation
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.KatachiCapability
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path

/**
 * V3 of the gutter's P5: what the gutter shows for the real `definition.json` of each sample (`contract/definition/real-*-definition.json`,
 * taken by the foundation's stage 4) and the real source files of that sample (copied to `contract/gutter/sources/<sample>/`), written
 * as a text golden (`contract/gutter/<sample>.txt`) and compared.
 *
 * Everything between the JSON and the text is the plugin's own: the definition is parsed by the real parser, [DefinitionLookup]
 * answers, the real PSI and line marker providers draw, and the real menu lists its rows. Only the edges are fakes (the state of the
 * definition is fixed to `Ready`, the platform actions are recording stand-ins). For each file the golden holds the roles, each ◆
 * (line, tooltip), the line of the `ktFileContent { }` its tooltip names (read from the sample's own `*Role.kt`), the rows of the
 * menu, and, for sample/jvm, the annotations of the SARIF of a check that found violations.
 *
 * Taking the goldens again after the sample, the definition or the gutter changed:
 *
 * ```
 * ./gradlew -p katachi-intellij-plugin test --tests '*RealDefinitionGutterTest*' -Dkatachi.gutter.golden.update=true
 * ```
 *
 * Read the diff before it goes in: a changed ◆ is a decision, not noise. Taking the sources again (the sample's files moved) is
 * `contract/gutter/sources/<sample>/<relative path>` copied from `sample/<sample>/` for every file the definition's `matchedFiles`
 * lists (the extensions the gutter reads: kt, kts, md, properties, toml, xml, txt, conf, json, pro) and every file the SARIF names;
 * fixtures of the definition and SARIF are taken as the doc of `RealDefinitionContractTest` says.
 */
internal class RealDefinitionGutterTest : AnnotatorTestBase() {
    private lateinit var fileProvider: KatachiFileLineMarkerProvider
    private lateinit var service: LookupDefinitionService
    private lateinit var root: Path

    override fun setUp() {
        super.setUp()
        fileProvider = KatachiFileLineMarkerProvider { ports() }
        LineMarkerProviders.getInstance().addExplicitExtension(Language.ANY, fileProvider, testRootDisposable)
        actions[GutterMenu.RUN_CHECK] = RecordingAction("Run the check")
        actions[GutterMenu.REFRESH] = RecordingAction("Refresh the definition")
    }

    /** The ports of the base, but the definition is the one over the real JSON. */
    override fun ports(): GutterPorts {
        val base = super.ports()
        if (!::service.isInitialized) return base
        return GutterPorts(base.hasDefinitionModule, { service }, base.violations, base.fileState, base.effects, base.seeds, base.action, base.notify, base.settings)
    }

    fun `test sample jvm の実 definition json で 各ファイルの ◆ とメニューと違反の下線が golden のとおりになる`() = checkGolden("jvm", violationsSarif = "real-jvm-violations-sarif", cleanSarif = "real-jvm-clean-sarif")

    fun `test sample android の実 definition json で 各ファイルの ◆ とメニューが golden のとおりになる`() = checkGolden("android")

    fun `test sample kmp の実 definition json で 各ファイルの ◆ とメニューが golden のとおりになる`() = checkGolden("kmp")

    private fun checkGolden(sample: String, violationsSarif: String? = null, cleanSarif: String? = null) {
        enter(sample)
        val problems = mutableListOf<String>()
        val text = buildString {
            val files = GutterSources.files(sample)
            val blocks = files.map { relative -> blockOf(sample, relative, problems) }
            appendLine("# gutter golden: sample/$sample")
            appendLine("# definition: contract/definition/real-$sample-definition.json, sources: contract/gutter/sources/$sample/ (copies of the sample's files)")
            appendLine("# update: ./gradlew -p katachi-intellij-plugin test --tests '*RealDefinitionGutterTest*' -Dkatachi.gutter.golden.update=true")
            appendLine("# files: ${files.size}, with a ◆: ${blocks.count { it.marks > 0 }}, ◆: ${blocks.sumOf { it.marks }}")
            blocks.forEach { appendLine(); append(it.text) }
            appendLine()
            append(annotationSection(sample, violationsSarif, cleanSarif, files, problems))
        }
        assertEquals("what a user would call wrong in the golden (a site that does not resolve, a rule outside any ktFileContent, a menu row that cannot be chosen)", emptyList<String>(), problems)
        GoldenFile.verify(GutterSources.goldenFile(sample), text, "build/gutter-golden-actual/$sample.txt")
    }

    // --- the world ---

    /** The sample's sources on disk under a project root the files are found from, and the service over its definition. */
    private fun enter(sample: String) {
        useLocalFiles()
        root = lightPath("x").parent
        GutterSources.copyTo(sample, root)
        val rootFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root) ?: error("$root was not found after it was written")
        VfsUtil.markDirtyAndRefresh(false, true, true, rootFile)
        val parsed = DefinitionFixtures.parsed("real-$sample-definition")
        val module = KatachiModule(":architecture-test", root.resolve("architecture-test"), root, sample, setOf(KatachiCapability.Definition))
        val snapshot = DefinitionSnapshot(module, parsed.definition.copy(projectRoot = root), parsed.templates, parsed.definition.katachiVersion, DEFINITION_INSTANT)
        service = LookupDefinitionService(snapshot, root)
    }

    private class Block(val text: String, val marks: Int)

    // --- one file ---

    private fun blockOf(sample: String, relative: String, problems: MutableList<String>): Block {
        val path = root.resolve(relative)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: error("$path was not found in the VFS")
        val file: PsiFile = runReadActionBlocking { PsiManager.getInstance(project).findFile(virtualFile) } ?: error("no PSI for $relative")
        val source = Files.readString(path)
        val roles = service.forPath(path)
        // The daemon asks the Kotlin provider about Kotlin PSI only, and the all-language one leaves Kotlin to it: the same split here.
        val infos = collectedInfos(file, if (file is KtFile) provider else fileProvider).sortedBy { it.startOffset }
        val text = buildString {
            appendLine("== $relative")
            appendLine("roles: " + describeRoles(roles?.matches?.map { it.role.qualifiedName }.orEmpty(), roles?.outcome))
            for (match in roles?.matches.orEmpty()) {
                val site = match.role.declaredAt
                if (site == null) {
                    problems += "$relative: ${match.role.qualifiedName} has no declaredAt"
                    continue
                }
                appendLine("    ${match.role.qualifiedName} declared at ${site.fileName}:${site.line} | ${roleLine(site, problems, relative, match.role.qualifiedName)}")
            }
            for (info in infos) {
                val line = source.substring(0, info.startOffset).count { it == '\n' } + 1
                val tooltip = info.lineMarkerTooltip.orEmpty()
                appendLine("$line | ◆ | ${tooltip.replace("\n", " ⏎ ")}")
                for (site in SITE.findAll(tooltip).map { DeclarationSiteRef(it.groupValues[1], it.groupValues[2].toInt()) }) {
                    appendLine("    allowed at ${site.fileName}:${site.line} | ${ruleLine(site, problems, "$relative:$line")}")
                }
                val menu = info.navigationHandler as? GutterMenu
                if (menu == null) problems += "$relative:$line: the ◆ has no menu"
                val rows = menu?.shown().orEmpty()
                checkSiblings(relative, rows, problems)
                rows.forEach { row ->
                    appendLine("    menu | $row")
                    if ((row.startsWith("(disabled)") && INFORMATION_ROWS.none { row.startsWith(it) }) || "not found" in row) problems += "$relative:$line: a menu row cannot be chosen: $row"
                }
            }
            if (infos.isEmpty()) appendLine("(no ◆)")
        }
        checkDeclarationsBound(relative, source, service.fileDeclarationsOf(virtualFile), infos.map { info -> source.substring(0, info.startOffset).count { it == '\n' } + 1 }, problems)
        return Block(text, infos.size)
    }

    /** The line a role was declared at: it must be a `.role {` line, or the call (`gradle()`, `gradle { }`) of the preset that made the role, in the file the definition names. */
    private fun roleLine(site: DeclarationSiteRef, problems: MutableList<String>, where: String, role: String): String {
        val path = service.fileOf(site)
        val text = path?.let { Files.readAllLines(it).getOrNull(site.line - 1) }
        if (text == null) {
            problems += "$where: $role is declared at ${site.fileName}:${site.line}, which is not a line of a single file under the test sources"
            return "(not found)"
        }
        // Written by hand it is the `"Name".role {` line; a role a preset makes (`gradle()`) is declared at the line that calls the preset.
        val preset = role.substringBefore('.').lowercase()
        if ("role" !in text && !Regex("""\b${Regex.escape(preset)}\s*[({]""").containsMatchIn(text.lowercase())) problems += "$where: $role is declared at ${site.fileName}:${site.line}, which neither says role nor calls $preset: ${text.trim()}"
        return text.trim()
    }

    /**
     * The other files of a role as the menu lists them must be the role's other `matchedFiles`, by name, with the count in the heading;
     * the list is cut where the menu says "… and N more".
     */
    private fun checkSiblings(relative: String, rows: List<String>, problems: MutableList<String>) {
        val definition = service.definitions.single().definition ?: return
        var index = 0
        while (index < rows.size) {
            val heading = SIBLINGS_HEADING.matchEntire(rows[index])
            index++
            if (heading == null) continue
            val roleName = heading.groupValues[1]
            val declaredCount = heading.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            val role = definition.roles.single { it.qualifiedName == roleName }
            val others = role.layout.flatMap { it.matchedFiles }.distinct().filter { it != relative }
            if (declaredCount != others.size) problems += "$relative: the menu says $declaredCount other files of $roleName, the definition has ${others.size}"
            val listed = mutableListOf<String>()
            var more = 0
            while (index < rows.size && rows[index] != "-----" && !rows[index].startsWith("==")) {
                val row = rows[index++]
                val cut = MORE_ROW.matchEntire(row)
                when {
                    cut != null -> more = cut.groupValues[1].toInt()
                    !row.startsWith("(disabled)") -> listed += row.substringBefore(" | ")
                }
            }
            val names = others.map { it.substringAfterLast('/') }
            if (!listed.all { it in names }) problems += "$relative: the menu lists ${listed - names.toSet()} as other files of $roleName, which are not in its matchedFiles"
            if (listed.size + more != others.size) problems += "$relative: the menu lists ${listed.size} (+$more more) of ${others.size} other files of $roleName"
        }
    }

    /**
     * When one role's `ktFileContent { }` restricts the declarations, every declaration the definition lists has a ◆ on the line its name
     * is written on (the definition's own line may be the annotation or the KDoc above it), in the order of the file.
     */
    private fun checkDeclarationsBound(
        relative: String,
        source: String,
        explanations: List<RoleFileDeclarations>,
        markLines: List<Int>,
        problems: MutableList<String>,
    ) {
        val declarations = explanations.singleOrNull()?.declarations?.takeIf { it.declarationsChecked }?.declarations ?: return
        val sourceLines = source.lines()
        val sorted = declarations.sortedBy { it.line }
        if (sorted.size != markLines.size) {
            problems += "$relative: the definition explains ${sorted.size} declarations (${sorted.map { it.name }}), the gutter draws ${markLines.size} ◆ (lines $markLines)"
            return
        }
        sorted.zip(markLines).forEach { (declaration, line) ->
            val text = sourceLines.getOrNull(line - 1).orEmpty()
            if (!Regex("""\b${Regex.escape(declaration.name)}\b""").containsMatchIn(text) || line < declaration.line) {
                problems += "$relative: the ◆ of ${declaration.name} (the definition says line ${declaration.line}) is on line $line: ${text.trim()}"
            }
        }
    }

    private fun describeRoles(names: List<String>, outcome: MatchOutcome?): String = when {
        names.isNotEmpty() -> names.joinToString(", ")
        outcome == null -> "(not under the definition)"
        else -> "(none) ${outcome::class.simpleName}"
    }

    /** The text of the line [site] names, and whether it is written inside a `ktFileContent { }` block (otherwise a problem). */
    private fun ruleLine(site: DeclarationSiteRef, problems: MutableList<String>, where: String): String {
        val path = service.fileOf(site)
        if (path == null) {
            problems += "$where: ${site.fileName}:${site.line} names no single file under the test sources"
            return "(not found)"
        }
        val lines = Files.readAllLines(path)
        val text = lines.getOrNull(site.line - 1)
        if (text == null) {
            problems += "$where: ${site.fileName} has no line ${site.line}"
            return "(no such line)"
        }
        if (!isInsideKtFileContent(lines, site.line - 1)) problems += "$where: ${site.fileName}:${site.line} is not inside a ktFileContent block: ${text.trim()}"
        return text.trim()
    }

    /** Walks up through the lines that enclose [index] (smaller indent, closing braces and comments skipped) looking for the `ktFileContent {` line. */
    private fun isInsideKtFileContent(lines: List<String>, index: Int): Boolean {
        var indent = indentOf(lines[index])
        for (j in index - 1 downTo 0) {
            val trimmed = lines[j].trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("}")) continue
            if (indentOf(lines[j]) < indent) {
                if (trimmed.startsWith("ktFileContent")) return true
                indent = indentOf(lines[j])
            }
        }
        return false
    }

    private fun indentOf(line: String): Int = line.length - line.trimStart().length

    // --- the annotations ---

    /** Only sample/jvm has the SARIF of a real run; the other two samples say so. */
    private fun annotationSection(sample: String, violationsSarif: String?, cleanSarif: String?, files: List<String>, problems: MutableList<String>): String {
        if (violationsSarif == null || cleanSarif == null) return "## annotations\n(no SARIF of a real run was taken for sample/$sample: only sample/jvm's)\n"
        val module = service.definitions.single().module.id
        val sarifFile = root.resolve("architecture-test/build/reports/katachi/katachi.sarif")
        fun results(name: String): List<ViolationModel> =
            parseSarifJson(DefinitionFixtures.sarifText(name).replace("file:///__ROOT__/", "file://$root/"), sarifFile, module)

        val found = results(violationsSarif)
        val cited = found.mapNotNull { it.location?.path }.distinct().map { root.relativize(it).toString() }.sorted()
        problems += cited.filter { it !in files }.map { "the SARIF names $it, which is not among the copied sources" }
        val watched = (cited + "src/main/kotlin/com/example/service/HealthService.kt").distinct().sorted()
        return buildString {
            appendLine("## annotations after the check that found nothing (contract/json/$cleanSarif.json)")
            val clean = results(cleanSarif)
            watched.forEach { appendLine(annotationsOf(it, clean)) }
            appendLine()
            appendLine("## annotations after the check that found violations (contract/json/$violationsSarif.json: ${found.size} results)")
            watched.forEach { appendLine(annotationsOf(it, found)) }
        }
    }

    private var checks = 0L

    /** Opens [relative] in an editor after the check with [results] and lists what the daemon underlines. */
    private fun annotationsOf(relative: String, results: List<ViolationModel>): String {
        violations.emit(ViolationsState.Ready(results, DEFINITION_INSTANT.plusSeconds(++checks), false, listOf(LIGHT_SARIF)))
        open(relative, Files.readString(root.resolve(relative)))
        val drawn = annotated()
        return buildString {
            append("== $relative")
            if (drawn.isEmpty()) append("\n(none)")
            drawn.forEach { append("\n").append(describe(it)) }
        }
    }

    private fun describe(annotation: DrawnAnnotation): String {
        val where = annotation.line?.let { "$it | ${annotation.severity.name} | underline `${annotation.text}`" } ?: "file | ${annotation.severity.name} | banner over the whole file"
        val first = annotation.description.lineSequence().first()
        return buildString {
            append(where).append(" | ").append(first)
            // The rule the message names must be a real line of the sample (here the constraint's own line).
            DECLARED_AT.find(first)?.let { match ->
                val site = DeclarationSiteRef(match.groupValues[1], match.groupValues[2].toInt())
                append("\n    rule at ${site.fileName}:${site.line} | ${service.fileOf(site)?.let { Files.readAllLines(it).getOrNull(site.line - 1)?.trim() } ?: "(not found)"}")
            }
            annotation.fixes.forEach { append("\n    fix | ").append(it) }
        }
    }

    private companion object {
        /** `(ServiceRole.kt:80)` in a tooltip: where the line that allowed the declaration was written. */
        /** The rows that are shown disabled on purpose: nothing to list, and the count of the siblings the list cut. */
        val INFORMATION_ROWS = listOf("(disabled) — No other file yet", "(disabled) … and ")

        val SIBLINGS_HEADING = Regex("""== Other files of (\S+?)(?: \((\d+)\))?""")
        val MORE_ROW = Regex("""\(disabled\) … and (\d+) more""")

        val DECLARED_AT = Regex("""declared at ([\w.\-]+\.kt):(\d+)""")

        val SITE = Regex("""\(([\w.\-]+\.kt):(\d+)\)""")
    }
}

/** A text golden under `src/test/resources`: compared, or written when `-Dkatachi.gutter.golden.update=true` reached the test JVM. */
internal object GoldenFile {
    const val UPDATE_PROPERTY: String = "katachi.gutter.golden.update"

    fun verify(golden: java.io.File, actual: String, actualCopy: String) {
        if (System.getProperty(UPDATE_PROPERTY) == "true") {
            golden.parentFile.mkdirs()
            golden.writeText(actual)
            return
        }
        if (!golden.isFile) throw AssertionError("${golden.path} does not exist. Write it with -D$UPDATE_PROPERTY=true and read it before it goes in.")
        val expected = golden.readText()
        if (expected == actual) return
        val copy = java.io.File(actualCopy).also { it.parentFile.mkdirs() }
        copy.writeText(actual)
        val expectedLines = expected.lines()
        val actualLines = actual.lines()
        val first = (0 until maxOf(expectedLines.size, actualLines.size)).first { expectedLines.getOrNull(it) != actualLines.getOrNull(it) }
        throw AssertionError(
            "${golden.path} differs from what the gutter shows, first at line ${first + 1}:\n" +
                "  golden: ${expectedLines.getOrNull(first)}\n  actual: ${actualLines.getOrNull(first)}\n" +
                "The whole text is in ${copy.path}; if the change is wanted, write the golden with -D$UPDATE_PROPERTY=true and read the diff.",
        )
    }
}
