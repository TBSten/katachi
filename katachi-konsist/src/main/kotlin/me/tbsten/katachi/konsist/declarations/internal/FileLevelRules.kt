package me.tbsten.katachi.konsist.declarations.internal

import com.lemonappdev.konsist.core.util.KotlinFileParser
import java.io.File
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind

// File annotations (`@file:Suppress(...)`) are allowed like imports: one no line allows is a violation, a line only allows.
// `actualsIn(...)` asks each `expect` declaration of the file for an `actual` one in the same directory of other source sets.

internal class FileAnnotationRule(val annotation: AnnotationRule, val prohibited: Boolean, val declaredAt: DeclarationSite) {
    override fun toString(): String = "FileAnnotationRule(${renderFileAnnotation(this)})"
}

internal class FileAnnotationFact(val annotation: AnnotationFact, val written: String, val line: Int?) {
    override fun toString(): String = "FileAnnotationFact($written)"
}

internal class ActualsRule(val sourceSets: List<String>, val declaredAt: DeclarationSite) {
    override fun toString(): String = "ActualsRule(${renderActuals(this)})"
}

internal fun renderFileAnnotation(rule: FileAnnotationRule): String =
    (if (rule.prohibited) "not " else "") + "@file:${rule.annotation.written.removePrefix("@").removePrefix("file:")}"

internal fun renderActuals(rule: ActualsRule): String = "actualsIn(${rule.sourceSets.joinToString(", ")})"

internal fun FileBuilder.fileAnnotationRule(pattern: String, prohibited: Boolean): FileAnnotationRule {
    val written = pattern.trim().removePrefix("@").removePrefix("file:")
    declarations.ensureCurrent((if (prohibited) "not " else "") + "@file:$written")
    val declaredAt = DeclarationSite.capture()
    return FileAnnotationRule(session.patterns.annotationRule(written, KtFileContentPatternKind.FileAnnotation), prohibited, declaredAt)
}

internal fun FileBuilder.actualsRule(sourceSets: List<String>): ActualsRule {
    val line = "actualsIn(${sourceSets.joinToString(", ")})"
    declarations.ensureCurrent(line)
    val declaredAt = DeclarationSite.capture()
    val names = sourceSets.map { it.trim() }
    if (names.isEmpty() || names.any { it.isEmpty() || '/' in it }) {
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.ActualsWithoutSourceSets, line, declaredAt)
    }
    return ActualsRule(names, declaredAt)
}

internal class UnexpectedFileAnnotation(val annotation: FileAnnotationFact, val allowed: List<FileAnnotationRule>) : KtFileFinding

internal class ProhibitedFileAnnotation(val annotation: FileAnnotationFact, val rule: FileAnnotationRule) : KtFileFinding

// file: where the actual was looked for, project relative, with `*` where any platform suffix may be.
internal class MissingActual(val expect: DeclarationFact, val sourceSet: String, val file: String, val rule: ActualsRule) : KtFileFinding

internal fun judgeFileAnnotations(rules: List<FileAnnotationRule>, facts: List<FileAnnotationFact>): List<KtFileFinding> {
    val allowed = rules.filterNot { it.prohibited }
    return facts.mapNotNull { fact ->
        val prohibitedBy = rules.firstOrNull { it.prohibited && annotationMatches(it.annotation, fact.annotation) }
        when {
            prohibitedBy != null -> ProhibitedFileAnnotation(fact, prohibitedBy)
            allowed.any { annotationMatches(it.annotation, fact.annotation) } -> null
            else -> UnexpectedFileAnnotation(fact, allowed)
        }
    }
}

private val SOURCE_SET_DIRECTORY: Regex = Regex("""(?:^|/)src/([^/]+)/""")

// The actual of `Platform.kt` may be in `Platform.kt` or in a file with a platform suffix, `Platform.android.kt`.
internal fun judgeActuals(rules: List<ActualsRule>, facts: KtFileFacts, projectRoot: File): List<KtFileFinding> {
    if (rules.isEmpty()) return emptyList()
    val expects = facts.declarations.filter { ModifierName.Expect in it.modifiers }
    if (expects.isEmpty()) return emptyList()
    val path = facts.path
    val sourceSet = SOURCE_SET_DIRECTORY.findAll(path).lastOrNull()
    val baseName = path.substringAfterLast('/').removeSuffix(".kt")
    return rules.flatMap { rule ->
        rule.sourceSets.flatMap { name ->
            val directory = sourceSet?.let { found ->
                path.substring(0, found.groups[1]!!.range.first) + name + path.substring(found.groups[1]!!.range.last + 1).substringBeforeLast('/')
            }
            val shown = (directory ?: "src/$name/...") + "/$baseName{,.*}.kt"
            val actuals = directory?.let { actualsIn(File(projectRoot, it), baseName) }.orEmpty()
            expects.filter { expect -> actuals.none { it.kind == expect.kind && it.name == expect.name } }
                .map { MissingActual(it, name, shown, rule) }
        }
    }
}

private fun actualsIn(directory: File, baseName: String): List<DeclarationFact> {
    val files = directory.listFiles { file ->
        file.isFile && (file.name == "$baseName.kt" || (file.name.startsWith("$baseName.") && file.name.endsWith(".kt")))
    }.orEmpty()
    return files.sortedBy { it.name }.flatMap { file ->
        ktFileFactsOf(KotlinFileParser.getKoFile(file), file.invariantSeparatorsPath).declarations
            .filter { ModifierName.Actual in it.modifiers }
    }
}

internal fun unexpectedFileAnnotationFailure(finding: UnexpectedFileAnnotation, path: String, site: DeclarationSite): FileConstraintFailure {
    val written = finding.annotation.written
    val reason = if (finding.allowed.isEmpty()) {
        "ktFileContent { } allows no file annotation here"
    } else {
        "not in the allowed file annotations (${finding.allowed.joinToString(", ") { it.annotation.written }})"
    }
    return FileConstraintFailure(
        file = path,
        declaration = written,
        line = finding.annotation.line,
        reasons = listOf(FileConstraintReason(NOT_ALLOWED_LABEL, reason)),
        howToFix = listOf(
            FileConstraintFix("Remove the file annotation"),
            FileConstraintFix(
                "If it is fine here, allow it at $site:",
                code = listOf("fileAnnotation(${kotlinString(finding.annotation.annotation.simpleName)})"),
            ),
        ),
        ruleDeclaredAt = site,
    )
}

internal fun prohibitedFileAnnotationFailure(finding: ProhibitedFileAnnotation, path: String): FileConstraintFailure = FileConstraintFailure(
    file = path,
    declaration = finding.annotation.written,
    line = finding.annotation.line,
    reasons = listOf(
        FileConstraintReason(NOT_ALLOWED_LABEL, finding.annotation.written),
        FileConstraintReason("Prohibited by", renderFileAnnotation(finding.rule)),
    ),
    howToFix = listOf(FileConstraintFix("Remove the file annotation")),
    ruleDeclaredAt = finding.rule.declaredAt,
)

internal fun missingActualFailure(finding: MissingActual, path: String): FileConstraintFailure {
    val expect = finding.expect
    val actual = renderFact(expect).replaceFirst("expect", "actual")
    return FileConstraintFailure(
        file = path,
        declaration = expect.name,
        line = expect.line,
        reasons = listOf(
            FileConstraintReason("Missing actual", "$actual in ${finding.sourceSet}"),
            FileConstraintReason("Looked in", finding.file),
        ),
        howToFix = listOf(
            FileConstraintFix("Write the actual declaration in ${finding.file}"),
            FileConstraintFix("Or remove ${finding.sourceSet} from the line at ${finding.rule.declaredAt}"),
        ),
        ruleDeclaredAt = finding.rule.declaredAt,
    )
}

private const val NOT_ALLOWED_LABEL: String = "Not allowed"
