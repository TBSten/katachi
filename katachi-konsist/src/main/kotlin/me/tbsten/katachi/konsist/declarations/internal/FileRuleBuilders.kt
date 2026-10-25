package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind

/** Everything one run of the block writes for the file itself: its package lines, import lines and declarations. */
internal class FileBuilder(
    val session: EvaluationSession,
    /** Where `ktFileContent { }` was called. */
    val declaredAt: DeclarationSite,
) {
    val declarations: DeclarationListBuilder = DeclarationListBuilder(session, Placement.File, declaredAt)
    private val packages = mutableListOf<PackageLineBuilder>()
    private val imports = mutableListOf<ImportLineBuilder>()

    /**
     * Registers an import line.
     *
     * @throws KatachiKtFileContentLineException when written inside the `{ }` of a class, or after the run ended.
     * @throws me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException when
     *   [pattern] is not a path katachi reads.
     */
    fun declareImport(pattern: String, prohibited: Boolean): ImportLineBuilder {
        declarations.ensureCurrent(renderImport(pattern, prohibited, emptyList()))
        val declaredAt = DeclarationSite.capture()
        val path = session.patterns.pathRule(pattern, KtFileContentPatternKind.Import)
        return ImportLineBuilder(session, path, prohibited, declaredAt).also { imports += it }
    }

    /**
     * Registers a package line naming a pattern.
     *
     * @throws KatachiKtFileContentLineException when written inside the `{ }` of a class, or after the run ended.
     * @throws me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException when
     *   [pattern] is not a path katachi reads.
     */
    fun declarePackage(pattern: String, prohibited: Boolean): PackageLineBuilder {
        declarations.ensureCurrent(renderPackage(pattern, prohibited, emptyList()))
        val declaredAt = DeclarationSite.capture()
        val rule = session.patterns.pathRule(pattern, KtFileContentPatternKind.Package)
        return PackageLineBuilder(session, PackageTarget.Pattern(rule), prohibited, declaredAt).also { packages += it }
    }

    /**
     * Registers `` `package`(matchingDirectory) ``.
     *
     * @throws KatachiKtFileContentLineException when written inside the `{ }` of a class, or after the run ended.
     */
    fun declareMatchingDirectory(): PackageLineBuilder {
        declarations.ensureCurrent(renderPackage(THE_FILES_DIRECTORY, prohibited = false, except = emptyList()))
        val declaredAt = DeclarationSite.capture()
        return PackageLineBuilder(session, PackageTarget.MatchingDirectory, prohibited = false, declaredAt = declaredAt)
            .also { packages += it }
    }

    /** Freezes what was written. A kind no line was written for comes out as an empty allow list. */
    fun build(): FileRules = FileRules(
        packages = packages.map { it.build() },
        imports = imports.map { it.build() },
        declarations = declarations.build(),
        declaredAt = declaredAt,
    )

    override fun toString(): String =
        "FileBuilder(${packages.size} package lines, ${imports.size} import lines, $declarations)"
}

/** One import line while `.except(...)` can still change it. */
internal class ImportLineBuilder(
    private val session: EvaluationSession,
    private val path: NameRule,
    private val prohibited: Boolean,
    private val declaredAt: DeclarationSite,
) {
    private val except = mutableListOf<NameRule>()

    /** The line as written so far. */
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

/** One package line while `.except(...)` can still change it. */
internal class PackageLineBuilder(
    private val session: EvaluationSession,
    private val target: PackageTarget,
    private val prohibited: Boolean,
    private val declaredAt: DeclarationSite,
) {
    private val except = mutableListOf<NameRule>()

    /** The line as written so far. */
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

/** A chained call reached after the run ended changes nothing anyone reads, so it is refused. */
private fun ensureOpen(session: EvaluationSession, line: String) {
    if (session.isOpen) return
    throw KatachiKtFileContentLineException(KtFileContentLineProblem.OutsideItsBlock, line, DeclarationSite.capture())
}
