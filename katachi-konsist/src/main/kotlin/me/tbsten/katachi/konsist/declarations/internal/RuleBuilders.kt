package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.ClassBodyScope
import me.tbsten.katachi.konsist.declarations.ClassBodyScopeImpl
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind

/*
 * What one evaluation of the block writes into, line by line, before it is frozen into the model
 * of `KtFileContentRules.kt`. A line is registered when `allow(...)` is called, and the calls
 * chained after it (`.superclass(...)`, `.optional()`) change that same line.
 */

/**
 * One run of the block: which list a new line goes to, and whether the run is still going.
 *
 * A line is written into the list of the block whose vocabulary made it, and only while that block
 * is the innermost one being run. That keeps a value kept in a variable from putting a line into
 * another block, or into a run that already ended, without anyone noticing.
 */
internal class EvaluationSession(val input: EvaluationInput) {
    private var current: DeclarationListBuilder? = null
    private var closed = false

    val patterns: PatternCache get() = input.patterns

    /** The values of the vocabulary written in this run, to find the ones no line was written with. */
    val values: ValueTraces = ValueTraces()

    /** Whether the run has not ended yet. */
    val isOpen: Boolean get() = !closed

    /** Whether [list] is the innermost block being run, the only one a new line may go to. */
    fun isCurrent(list: DeclarationListBuilder): Boolean = !closed && current === list

    /** Runs [body] with [list] as the innermost block. */
    fun <T> within(list: DeclarationListBuilder, body: () -> T): T {
        val outer = current
        current = list
        try {
            return body()
        } finally {
            current = outer
        }
    }

    /**
     * @throws KatachiKtFileContentLineException when a value of the vocabulary -- a modifier, an
     *   annotation, a kind, `anythingElse` -- was written on a line of its own and never passed to `allow(...)`.
     */
    fun failOnDanglingValues() {
        val dangling = values.firstDangling() ?: return
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.DanglingModifiers, dangling.text(), dangling.declaredAt)
    }

    /** Ends the run: nothing can be written into it any more. */
    fun close() {
        closed = true
        current = null
    }

    override fun toString(): String = "EvaluationSession($input, open=$isOpen)"
}

/** Something a declaration list holds: a declaration line still being written, or a finished `anythingElse` line. */
internal sealed interface DeclarationListEntryBuilder {
    fun build(): DeclarationListEntry
}

/** An entry that is complete as soon as it is written. */
private class WrittenEntry(private val entry: DeclarationListEntry) : DeclarationListEntryBuilder {
    override fun build(): DeclarationListEntry = entry
}

/** The lines of the declarations directly in a file, or of the members of one class. */
internal class DeclarationListBuilder(
    val session: EvaluationSession,
    val placement: Placement,
    /** The `ktFileContent { }` call for a file; the class line for a class's members. */
    val declaredAt: DeclarationSite,
) {
    private val entries = mutableListOf<DeclarationListEntryBuilder>()

    /** Whether a line was written, which is what makes the list an allow list at all. */
    val isWritten: Boolean get() = entries.isNotEmpty()

    /**
     * Registers a declaration line: candidates [raw] (not checked yet) named [name].
     *
     * @throws KatachiKtFileContentLineException when this list is not the block being written, or
     *   for a `typealias` inside a class.
     * @throws me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException when
     *   [name] is not a pattern katachi reads.
     * @throws me.tbsten.katachi.konsist.declarations.KatachiKtFileContentModifierException when no
     *   declaration can have the modifiers.
     */
    fun declare(raw: List<Shape>, prohibited: Boolean, name: String): DeclarationLineBuilder {
        val line = renderDeclaration(raw, name, prohibited)
        ensureCurrent(line)
        val declaredAt = DeclarationSite.capture()
        if (placement == Placement.ClassBody && raw.namesTypeAlias()) {
            throw KatachiKtFileContentLineException(KtFileContentLineProblem.TypeAliasInClassBody, line, declaredAt)
        }
        val nameRule = session.patterns.nameRule(name, KtFileContentPatternKind.Name)
        val shapes = validatedShapes(raw, placement, line, declaredAt)
        return DeclarationLineBuilder(this, shapes, nameRule, prohibited, declaredAt).also { entries += it }
    }

    /**
     * Registers an `allow(..., anythingElse)` that allows what matches one of [alternatives].
     *
     * @throws KatachiKtFileContentLineException when this list is not the block being written, or
     *   when the line starts with `not.`.
     * @throws me.tbsten.katachi.konsist.declarations.KatachiKtFileContentModifierException when a set
     *   of modifiers cannot be on one declaration, or not here.
     */
    fun addAnythingElse(alternatives: List<ModifierSet>, prohibited: Boolean) {
        val line = renderAnythingElseLine(alternatives, prohibited)
        ensureCurrent(line)
        val declaredAt = DeclarationSite.capture()
        if (prohibited) throw KatachiKtFileContentLineException(KtFileContentLineProblem.AnythingElseAfterNot, line, declaredAt)
        val kept = validatedAnythingElse(alternatives, placement, line, declaredAt)
        entries += WrittenEntry(AnythingElseRule(kept, declaredAt))
    }

    /** @throws KatachiKtFileContentLineException when this list is not the innermost block being run. */
    fun ensureCurrent(line: String) {
        if (session.isCurrent(this)) return
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.OutsideItsBlock, line, DeclarationSite.capture())
    }

    fun build(): DeclarationListRules = DeclarationListRules(placement, entries.map { it.build() }, declaredAt)

    override fun toString(): String = "DeclarationListBuilder($placement, ${entries.size} lines)"
}

/** One declaration line while the calls chained after `allow(...)` are still changing it. */
internal class DeclarationLineBuilder(
    private val list: DeclarationListBuilder,
    private val shapes: List<Shape>,
    private val name: NameRule,
    private val prohibited: Boolean,
    private val declaredAt: DeclarationSite,
) : DeclarationListEntryBuilder {
    private val superclasses = mutableListOf<SuperclassRule>()
    private var propertyType: TypeRule? = null
    private var parameterTexts: List<String>? = null
    private var parameters: List<ParameterRule>? = null
    private var parametersWritten = false
    private var returnType: TypeRule? = null
    private var members: DeclarationListBuilder? = null
    private var optional = false
    private val except = mutableListOf<NameRule>()

    private val session: EvaluationSession get() = list.session

    /** The line as written so far. */
    val text: String
        get() = renderDeclaration(
            shapes = shapes,
            name = name.written,
            prohibited = prohibited,
            superclasses = superclasses.map { it.written },
            propertyType = propertyType?.written,
            parameters = parameterTexts,
            returnType = returnType?.written,
            except = except.map { it.written },
        )

    fun addSuperclasses(names: List<String>) {
        ensureOpen()
        if (names.isEmpty()) throw problem(KtFileContentLineProblem.SuperclassWithoutNames)
        names.mapTo(superclasses) { session.patterns.superclassRule(it) }
    }

    fun writePropertyType(type: String) {
        propertyType = session.patterns.typeRule(type)
    }

    /** [written] `null` is `anyArgs()`: written, but asking nothing. Each one is `"name: Type"`. */
    fun writeParameters(written: List<String>?) {
        ensureOpen()
        if (parametersWritten) throw problem(KtFileContentLineProblem.ParametersWrittenTwice)
        parametersWritten = true
        val texts = written?.map { it.trim() }
        // Kept before reading them, so that the line an exception names shows the parameters as written.
        parameterTexts = texts
        parameters = texts?.map(::parameterRule)
    }

    /**
     * [written] split at its first `:`, which a Kotlin parameter type never holds, into a name
     * pattern and a type pattern.
     */
    private fun parameterRule(written: String): ParameterRule {
        val colon = written.indexOf(':')
        val name = if (colon < 0) "" else written.substring(0, colon).trim()
        val type = (if (colon < 0) written else written.substring(colon + 1)).trim()
        if (name.isEmpty()) throw parameterProblem(KtFileContentLineProblem.ParameterWithoutName, written)
        if (type.isEmpty()) throw parameterProblem(KtFileContentLineProblem.ParameterWithoutType, written)
        return ParameterRule(
            written = written,
            name = session.patterns.nameRule(name, KtFileContentPatternKind.ParameterName),
            type = session.patterns.typeRule(type),
        )
    }

    fun writeReturnType(type: String) {
        ensureOpen()
        if (returnType != null) throw problem(KtFileContentLineProblem.ReturnTypeWrittenTwice)
        returnType = session.patterns.typeRule(type)
    }

    /** Runs [body] as the members of this line, which from now on are an allow list of their own. */
    fun writeBody(body: ClassBodyScope.() -> Unit) {
        ensureOpen()
        if (prohibited) throw problem(KtFileContentLineProblem.BodyOnProhibited)
        if (members != null) throw problem(KtFileContentLineProblem.BodyWrittenTwice)
        val memberList = DeclarationListBuilder(session, Placement.ClassBody, declaredAt)
        members = memberList
        session.within(memberList) { ClassBodyScopeImpl(memberList).body() }
    }

    fun markOptional() {
        ensureOpen()
        if (prohibited) throw problem(KtFileContentLineProblem.OptionalOnProhibited)
        optional = true
    }

    fun addExcept(names: List<String>) {
        ensureOpen()
        if (!prohibited) throw problem(KtFileContentLineProblem.ExceptWithoutNot)
        if (names.isEmpty()) throw problem(KtFileContentLineProblem.ExceptWithoutNames)
        names.mapTo(except) { session.patterns.nameRule(it, KtFileContentPatternKind.Except) }
    }

    override fun build(): DeclarationRule = DeclarationRule(
        shapes = shapes,
        name = name,
        superclasses = superclasses.toList(),
        propertyType = propertyType,
        parameters = parameters,
        returnType = returnType,
        members = members?.build(),
        required = !prohibited && !optional && !name.hasWildcard,
        prohibited = prohibited,
        except = except.toList(),
        declaredAt = declaredAt,
    )

    /** A chained call reached after the run ended changes nothing anyone reads, so it is refused. */
    private fun ensureOpen() {
        if (session.isOpen) return
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.OutsideItsBlock, text, DeclarationSite.capture())
    }

    private fun problem(problem: KtFileContentLineProblem): KatachiKtFileContentLineException =
        KatachiKtFileContentLineException(problem, text, declaredAt)

    private fun parameterProblem(problem: KtFileContentLineProblem, parameter: String): KatachiKtFileContentLineException =
        KatachiKtFileContentLineException(problem, text, declaredAt, parameter = parameter)

    override fun toString(): String = "DeclarationLineBuilder($text)"
}
