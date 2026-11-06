package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.ClassBodyScope
import me.tbsten.katachi.konsist.declarations.ClassBodyScopeImpl
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind
import me.tbsten.katachi.konsist.declarations.Modifiers

// A line goes only into the list of the block whose vocabulary made it, and only while that block is the innermost one running:
// otherwise a value kept in a variable would put a line into another block, or an ended run, unnoticed.
internal class EvaluationSession(val input: EvaluationInput) {
    private var current: DeclarationListBuilder? = null
    private var closed = false

    val patterns: PatternCache get() = input.patterns

    val values: ValueTraces = ValueTraces()

    val isOpen: Boolean get() = !closed

    fun isCurrent(list: DeclarationListBuilder): Boolean = !closed && current === list

    fun <T> within(list: DeclarationListBuilder, body: () -> T): T {
        val outer = current
        current = list
        try {
            return body()
        } finally {
            current = outer
        }
    }

    fun failOnDanglingValues() {
        val dangling = values.firstDangling() ?: return
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.DanglingModifiers, dangling.text(), dangling.declaredAt)
    }

    fun close() {
        closed = true
        current = null
    }

    override fun toString(): String = "EvaluationSession($input, open=$isOpen)"
}

internal sealed interface DeclarationListEntryBuilder {
    fun build(): DeclarationListEntry
}

private class WrittenEntry(private val entry: DeclarationListEntry) : DeclarationListEntryBuilder {
    override fun build(): DeclarationListEntry = entry
}

internal class DeclarationListBuilder(
    val session: EvaluationSession,
    val placement: Placement,
    val declaredAt: DeclarationSite,
) {
    private val entries = mutableListOf<DeclarationListEntryBuilder>()

    fun declare(raw: List<Shape>, prohibited: Boolean, required: Boolean, name: String): DeclarationLineBuilder {
        val line = renderDeclaration(raw, name, prohibited)
        ensureCurrent(line)
        val declaredAt = DeclarationSite.capture()
        val nameRule = session.patterns.nameRule(name, KtFileContentPatternKind.Name)
        val shapes = validatedShapes(raw, placement, line, declaredAt)
        return DeclarationLineBuilder(this, shapes, nameRule, prohibited, required, declaredAt).also { entries += it }
    }

    fun addAnythingElse(alternatives: List<ModifierSet>, prohibited: Boolean) {
        val line = renderAnythingElseLine(alternatives, prohibited)
        ensureCurrent(line)
        val declaredAt = DeclarationSite.capture()
        if (prohibited) throw KatachiKtFileContentLineException(KtFileContentLineProblem.AnythingElseAfterNot, line, declaredAt)
        val kept = validatedAnythingElse(alternatives, placement, line, declaredAt)
        entries += WrittenEntry(AnythingElseRule(kept, declaredAt))
    }

    fun ensureCurrent(line: String) {
        if (session.isCurrent(this)) return
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.OutsideItsBlock, line, DeclarationSite.capture())
    }

    fun build(): DeclarationListRules = DeclarationListRules(placement, entries.map { it.build() }, declaredAt)

    override fun toString(): String = "DeclarationListBuilder($placement, ${entries.size} lines)"
}

internal class DeclarationLineBuilder(
    private val list: DeclarationListBuilder,
    private val shapes: List<Shape>,
    private val name: NameRule,
    private val prohibited: Boolean,
    private val required: Boolean,
    private val declaredAt: DeclarationSite,
) : DeclarationListEntryBuilder {
    private val superclasses = mutableListOf<SuperclassRule>()
    private var receiver: TypeRule? = null
    private var propertyType: TypeRule? = null
    private var parameterTexts: List<String>? = null
    private var parameters: List<ParameterRule>? = null
    private var parametersWritten = false
    private var returnType: TypeRule? = null
    private var members: DeclarationListBuilder? = null
    private val except = mutableListOf<NameRule>()
    private val details = mutableListOf<DetailRule>()

    private val session: EvaluationSession get() = list.session

    val text: String
        get() = renderDeclaration(
            shapes = shapes,
            name = name.written,
            prohibited = prohibited,
            receiver = receiver?.written,
            superclasses = superclasses.map { it.written },
            propertyType = propertyType?.written,
            parameters = parameterTexts,
            returnType = returnType?.written,
            except = except.map { it.written },
            details = details.map { it.rendered },
        )

    val kinds: Set<DeclarationKindName> get() = shapes.mapTo(LinkedHashSet()) { it.kind }

    // kinds: those of the line's kinds that have this detail; a line none of whose kinds has it is refused.
    fun addDetail(call: String, kinds: Set<DeclarationKindName>? = null, rule: () -> DetailRule) {
        ensureOpen()
        if (details.any { it.call == call }) throw detailProblem(KtFileContentLineProblem.DetailWrittenTwice, call)
        if (kinds != null && this.kinds.none { it in kinds }) throw detailProblem(KtFileContentLineProblem.DetailNotForKind, call)
        details += rule()
    }

    fun detailParameterRule(written: String, call: String): ParameterRule =
        session.patterns.parameterRule(written, allowProperty = call == "primaryConstructor") { problem -> parameterProblem(problem, written) }

    fun typeParameterRule(written: String): TypeParameterRule =
        session.patterns.typeParameterRule(written) { problem -> parameterProblem(problem, written) }

    fun detailNameRule(written: String): NameRule = session.patterns.nameRule(written, KtFileContentPatternKind.Delegate)

    fun detailTypeRule(written: String): TypeRule = session.patterns.typeRule(written)

    // A detail's modifiers are one set of modifiers, used up by the line like the modifiers in front of it.
    fun detailModifiers(call: String, modifiers: Modifiers): Set<ModifierName> {
        modifiers.word.trace.use()
        modifiers.word.requireIn(list) { text }
        val single = modifiers.alternatives.singleOrNull()
        if (modifiers.isOptional || single == null || single.annotations.isNotEmpty()) {
            throw detailProblem(KtFileContentLineProblem.DetailModifiers, call)
        }
        return single.modifiers
    }

    fun detailProblem(problem: KtFileContentLineProblem, call: String): KatachiKtFileContentLineException =
        KatachiKtFileContentLineException(problem, text, declaredAt, parameter = call)

    fun writeContext(parameters: List<String>) =
        addDetail("context") { ContextRule(parameters.map { detailParameterRule(it.trim(), "context") }) }

    fun writeTypeParameters(parameters: List<String>) =
        addDetail("typeParameters") { TypeParametersRule(parameters.map { typeParameterRule(it) }) }

    fun writeKDoc() = addDetail("kdoc") { KDocRule }

    fun writeBodyKind(kind: BodyKind) = addDetail("body") { BodyRule(kind) }

    fun writeGetter() = addDetail("getter") { GetterRule }

    fun writeSetter(modifiers: Modifiers?) = addDetail("setter", setOf(DeclarationKindName.Var)) {
        SetterRule(modifiers?.let { detailModifiers("setter", it) }.orEmpty())
    }

    fun writeDelegate(name: String) = addDetail("delegatedBy") { DelegateRule(detailNameRule(name)) }

    fun writeBackingField(type: String?) = addDetail("backingField") { BackingFieldRule(type?.let(::detailTypeRule)) }

    // parameters: null is `anyArgs()`.
    fun writePrimaryConstructor(modifiers: Modifiers?, parameters: List<String>?) =
        addDetail("primaryConstructor", setOf(DeclarationKindName.Class)) {
            PrimaryConstructorRule(
                modifiers = modifiers?.let { detailModifiers("primaryConstructor", it) }.orEmpty(),
                parameters = parameters?.map { detailParameterRule(it.trim(), "primaryConstructor") },
            )
        }

    fun addSuperclasses(names: List<String>) {
        ensureOpen()
        if (names.isEmpty()) throw problem(KtFileContentLineProblem.SuperclassWithoutNames)
        names.mapTo(superclasses) { session.patterns.superclassRule(it) }
    }

    fun writeReceiver(type: String) {
        receiver = session.patterns.receiverRule(type)
    }

    fun writePropertyType(type: String) {
        propertyType = session.patterns.typeRule(type)
    }

    // null is `anyArgs()`: written, but asking nothing.
    fun writeParameters(written: List<String>?) {
        ensureOpen()
        if (parametersWritten) throw problem(KtFileContentLineProblem.ParametersWrittenTwice)
        parametersWritten = true
        val texts = written?.map { it.trim() }
        // Kept before reading them, so that the line an exception names shows the parameters as written.
        parameterTexts = texts
        parameters = texts?.map(::parameterRule)
    }

    private fun parameterRule(written: String): ParameterRule =
        session.patterns.parameterRule(written) { problem -> parameterProblem(problem, written) }

    fun writeReturnType(type: String) {
        ensureOpen()
        if (returnType != null) throw problem(KtFileContentLineProblem.ReturnTypeWrittenTwice)
        returnType = session.patterns.typeRule(type)
    }

    fun writeBody(body: ClassBodyScope.() -> Unit) {
        ensureOpen()
        if (prohibited) throw problem(KtFileContentLineProblem.BodyOnProhibited)
        if (members != null) throw problem(KtFileContentLineProblem.BodyWrittenTwice)
        val memberList = DeclarationListBuilder(session, Placement.ClassBody, declaredAt)
        members = memberList
        session.within(memberList) { ClassBodyScopeImpl(memberList).body() }
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
        receiver = receiver,
        superclasses = superclasses.toList(),
        propertyType = propertyType,
        parameters = parameters,
        returnType = returnType,
        members = members?.build(),
        required = required,
        prohibited = prohibited,
        except = except.toList(),
        declaredAt = declaredAt,
        details = details.toList(),
    )

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
