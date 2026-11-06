package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.AnythingElse
import me.tbsten.katachi.konsist.declarations.AnythingElseAllow
import me.tbsten.katachi.konsist.declarations.ClassBodyScope
import me.tbsten.katachi.konsist.declarations.DeclarationAllow
import me.tbsten.katachi.konsist.declarations.DeclarationRequired
import me.tbsten.katachi.konsist.declarations.DeclarationListScopeImpl
import me.tbsten.katachi.konsist.declarations.FileProhibition
import me.tbsten.katachi.konsist.declarations.FunInterfaceAllow
import me.tbsten.katachi.konsist.declarations.FunInterfaceRequired
import me.tbsten.katachi.konsist.declarations.FunctionAllow
import me.tbsten.katachi.konsist.declarations.FunctionRequired
import me.tbsten.katachi.konsist.declarations.FunctionKind
import me.tbsten.katachi.konsist.declarations.FunctionLine
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.MemberProhibition
import me.tbsten.katachi.konsist.declarations.Modifiers
import me.tbsten.katachi.konsist.declarations.PropertyAllow
import me.tbsten.katachi.konsist.declarations.PropertyRequired
import me.tbsten.katachi.konsist.declarations.PropertyKind
import me.tbsten.katachi.konsist.declarations.PropertyLine
import me.tbsten.katachi.konsist.declarations.TypeAliasAllow
import me.tbsten.katachi.konsist.declarations.TypeAliasRequired
import me.tbsten.katachi.konsist.declarations.TypeAliasKind
import me.tbsten.katachi.konsist.declarations.TypeAliasLine
import me.tbsten.katachi.konsist.declarations.TypeAllow
import me.tbsten.katachi.konsist.declarations.TypeRequired
import me.tbsten.katachi.konsist.declarations.TypeKind
import me.tbsten.katachi.konsist.declarations.TypeLine

internal class AllowWriter(
    private val list: DeclarationListBuilder,
    private val prohibited: Boolean,
    private val required: Boolean = false,
) {
    fun type(modifiers: List<Modifiers>, kind: TypeKind, name: String, body: (ClassBodyScope.() -> Unit)?): TypeLine {
        val line = declare(modifiers, listOf(kind.word), kind.kinds.map { Shape(ModifierSet.EMPTY, it) }, name)
        if (body != null) line.writeBody(body)
        return TypeLine(line)
    }

    fun funInterface(
        modifiers: List<Modifiers>,
        fn: FunctionKind,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)?,
    ): TypeLine {
        val shapes = kind.kinds.map { Shape(ModifierSet.EMPTY + ModifierName.Fun, it) }
        val line = declare(modifiers, listOf(fn.word, kind.word), shapes, name)
        if (body != null) line.writeBody(body)
        return TypeLine(line)
    }

    fun function(modifiers: List<Modifiers>, kind: FunctionKind, name: String, receiver: String? = null): FunctionLine {
        val line = declare(modifiers, listOf(kind.word), listOf(Shape(ModifierSet.EMPTY, DeclarationKindName.Function)), name)
        if (receiver != null) line.writeReceiver(receiver)
        return FunctionLine(line)
    }

    fun property(modifiers: List<Modifiers>, kind: PropertyKind, name: String, type: String?, receiver: String? = null): PropertyLine {
        val line = declare(modifiers, listOf(kind.word), kind.kinds.map { Shape(ModifierSet.EMPTY, it) }, name)
        if (receiver != null) line.writeReceiver(receiver)
        if (type != null) line.writePropertyType(type)
        return PropertyLine(line)
    }

    fun typeAlias(modifiers: List<Modifiers>, kind: TypeAliasKind, name: String): TypeAliasLine =
        TypeAliasLine(declare(modifiers, listOf(kind.word), listOf(Shape(ModifierSet.EMPTY, DeclarationKindName.TypeAlias)), name))

    fun anythingElse(modifiers: List<Modifiers>, anything: AnythingElse) {
        val alternatives = alternativesOf(modifiers)
        val line = { renderAnythingElseLine(alternatives, prohibited) }
        use(modifiers.map { it.word } + anything.word, line)
        // `not.allow(..., anythingElse)` has a problem of its own, which says more than this one.
        if (!prohibited) refuseOptional(modifiers, KtFileContentLineProblem.OptionalOnAnythingElse, line)
        list.addAnythingElse(alternatives, prohibited)
    }

    private fun declare(modifiers: List<Modifiers>, kinds: List<VocabularyWord>, kindShapes: List<Shape>, name: String): DeclarationLineBuilder {
        val shapes = alternativesOf(modifiers)
            .flatMap { set -> kindShapes.map { Shape(set.union(it.modifiers), it.kind) } }
            .distinct()
        val line = { renderDeclaration(shapes, name, prohibited) }
        use(modifiers.map { it.word } + kinds, line)
        if (prohibited) refuseOptional(modifiers, KtFileContentLineProblem.OptionalAfterNot, line)
        return list.declare(shapes, prohibited, required, name)
    }

    private fun refuseOptional(modifiers: List<Modifiers>, problem: KtFileContentLineProblem, line: () -> String) {
        if (modifiers.none { it.isOptional }) return
        throw KatachiKtFileContentLineException(problem, line(), DeclarationSite.capture())
    }

    private fun use(words: List<VocabularyWord>, line: () -> String) {
        words.forEach { it.trace.use() }
        words.forEach { it.requireIn(list, line) }
    }

    private fun alternativesOf(modifiers: List<Modifiers>): List<ModifierSet> {
        val optional = modifiers.filter { it.isOptional }.map { value -> value.alternatives.flatMap { it.modifiers }.distinct() }
        return modifiers.filterNot { it.isOptional }.fold(listOf(ModifierSet.EMPTY.copy(optional = optional))) { sets, next ->
            val inOr = next.alternatives.flatMap { it.modifiers }.toSet()
            sets.flatMap { set ->
                next.alternatives.map { chosen -> set.union(chosen.copy(bundled = inOr - chosen.modifiers)) }
            }
        }.distinct()
    }

    override fun toString(): String = "AllowWriter($list, prohibited=$prohibited, required=$required)"
}

// Kotlin has no internal member in an interface, so the overloads reach the writer through these.

private fun writerOf(target: DeclarationAllow): AllowWriter = when (target) {
    is DeclarationListScopeImpl -> target.writer
    is FileProhibition -> target.writer
    is MemberProhibition -> target.writer
}

internal val TypeAllow.allowWriter: AllowWriter
    get() = when (this) {
        is DeclarationAllow -> writerOf(this)
    }

internal val FunInterfaceAllow.allowWriter: AllowWriter
    get() = when (this) {
        is DeclarationAllow -> writerOf(this)
    }

internal val FunctionAllow.allowWriter: AllowWriter
    get() = when (this) {
        is DeclarationAllow -> writerOf(this)
    }

internal val PropertyAllow.allowWriter: AllowWriter
    get() = when (this) {
        is DeclarationAllow -> writerOf(this)
    }

internal val TypeAliasAllow.allowWriter: AllowWriter
    get() = when (this) {
        is DeclarationAllow -> writerOf(this)
    }

internal val AnythingElseAllow.allowWriter: AllowWriter
    get() = when (this) {
        is DeclarationAllow -> writerOf(this)
    }

private fun requiredWriterOf(target: DeclarationRequired): AllowWriter = when (target) {
    is DeclarationListScopeImpl -> target.requiredWriter
}

internal val TypeRequired.requiredWriter: AllowWriter
    get() = when (this) {
        is DeclarationRequired -> requiredWriterOf(this)
    }

internal val FunInterfaceRequired.requiredWriter: AllowWriter
    get() = when (this) {
        is DeclarationRequired -> requiredWriterOf(this)
    }

internal val FunctionRequired.requiredWriter: AllowWriter
    get() = when (this) {
        is DeclarationRequired -> requiredWriterOf(this)
    }

internal val PropertyRequired.requiredWriter: AllowWriter
    get() = when (this) {
        is DeclarationRequired -> requiredWriterOf(this)
    }

internal val TypeAliasRequired.requiredWriter: AllowWriter
    get() = when (this) {
        is DeclarationRequired -> requiredWriterOf(this)
    }
