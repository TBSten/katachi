package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind

internal class FileBuilder(
    val session: EvaluationSession,
    val declaredAt: DeclarationSite,
) {
    val declarations: DeclarationListBuilder = DeclarationListBuilder(session, Placement.File, declaredAt)
    private val packages = mutableListOf<PackageLineBuilder>()
    private val imports = mutableListOf<ImportLineBuilder>()
    private val fileAnnotations = mutableListOf<FileAnnotationRule>()
    private val actuals = mutableListOf<ActualsRule>()

    fun declareFileAnnotation(pattern: String, prohibited: Boolean): FileAnnotationRule =
        fileAnnotationRule(pattern, prohibited).also { fileAnnotations += it }

    fun declareActuals(sourceSets: List<String>): ActualsRule = actualsRule(sourceSets).also { actuals += it }

    fun declareImport(pattern: String, prohibited: Boolean): ImportLineBuilder {
        declarations.ensureCurrent(renderImport(pattern, prohibited, emptyList()))
        val declaredAt = DeclarationSite.capture()
        val path = session.patterns.pathRule(pattern, KtFileContentPatternKind.Import)
        return ImportLineBuilder(session, path, prohibited, declaredAt).also { imports += it }
    }

    fun declarePackage(pattern: String, prohibited: Boolean): PackageLineBuilder {
        declarations.ensureCurrent(renderPackage(pattern, prohibited, emptyList()))
        val declaredAt = DeclarationSite.capture()
        val rule = session.patterns.pathRule(pattern, KtFileContentPatternKind.Package)
        return PackageLineBuilder(session, PackageTarget.Pattern(rule), prohibited, declaredAt).also { packages += it }
    }

    fun declareMatchingDirectory(): PackageLineBuilder {
        declarations.ensureCurrent(renderPackage(THE_FILES_DIRECTORY, prohibited = false, except = emptyList()))
        val declaredAt = DeclarationSite.capture()
        return PackageLineBuilder(session, PackageTarget.MatchingDirectory, prohibited = false, declaredAt = declaredAt)
            .also { packages += it }
    }

    fun build(): FileRules = FileRules(
        packages = packages.map { it.build() },
        imports = imports.map { it.build() },
        declarations = declarations.build(),
        declaredAt = declaredAt,
        fileAnnotations = fileAnnotations.toList(),
        actuals = actuals.toList(),
    )

    override fun toString(): String =
        "FileBuilder(${packages.size} package lines, ${imports.size} import lines, $declarations)"
}

internal class ImportLineBuilder(
    private val session: EvaluationSession,
    private val path: NameRule,
    private val prohibited: Boolean,
    private val declaredAt: DeclarationSite,
) {
    private val except = mutableListOf<NameRule>()

    val text: String get() = renderImport(path.written, prohibited, except.map { it.written })

    fun addExcept(patterns: List<String>) {
        ensureOpen(session, text)
        if (!prohibited) throw KatachiKtFileContentLineException(KtFileContentLineProblem.ExceptWithoutNot, text, declaredAt)
        if (patterns.isEmpty()) {
            throw KatachiKtFileContentLineException(KtFileContentLineProblem.ExceptWithoutNames, text, declaredAt)
        }
        patterns.mapTo(except) { session.patterns.pathRule(it, KtFileContentPatternKind.Except) }
    }

    fun build(): ImportRule = ImportRule(
        path = path,
        prohibited = prohibited,
        except = except.toList(),
        declaredAt = declaredAt,
    )

    override fun toString(): String = "ImportLineBuilder($text)"
}

internal class PackageLineBuilder(
    private val session: EvaluationSession,
    private val target: PackageTarget,
    private val prohibited: Boolean,
    private val declaredAt: DeclarationSite,
) {
    private val except = mutableListOf<NameRule>()

    val text: String
        get() = renderPackage(PackageRule(target, prohibited, except.toList(), declaredAt))

    fun addExcept(patterns: List<String>) {
        ensureOpen(session, text)
        if (!prohibited) throw KatachiKtFileContentLineException(KtFileContentLineProblem.ExceptWithoutNot, text, declaredAt)
        if (patterns.isEmpty()) {
            throw KatachiKtFileContentLineException(KtFileContentLineProblem.ExceptWithoutNames, text, declaredAt)
        }
        patterns.mapTo(except) { session.patterns.pathRule(it, KtFileContentPatternKind.Except) }
    }

    fun build(): PackageRule = PackageRule(target, prohibited, except.toList(), declaredAt)

    override fun toString(): String = "PackageLineBuilder($text)"
}

private fun ensureOpen(session: EvaluationSession, line: String) {
    if (session.isOpen) return
    throw KatachiKtFileContentLineException(KtFileContentLineProblem.OutsideItsBlock, line, DeclarationSite.capture())
}
