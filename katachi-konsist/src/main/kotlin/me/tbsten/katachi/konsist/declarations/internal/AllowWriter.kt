package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.konsist.declarations.AnythingElse
import me.tbsten.katachi.konsist.declarations.AnythingElseAllow
import me.tbsten.katachi.konsist.declarations.ClassBodyScope
import me.tbsten.katachi.konsist.declarations.DeclarationAllow
import me.tbsten.katachi.konsist.declarations.DeclarationListScopeImpl
import me.tbsten.katachi.konsist.declarations.FileProhibition
import me.tbsten.katachi.konsist.declarations.FunInterfaceAllow
import me.tbsten.katachi.konsist.declarations.FunctionAllow
import me.tbsten.katachi.konsist.declarations.FunctionKind
import me.tbsten.katachi.konsist.declarations.FunctionLine
import me.tbsten.katachi.konsist.declarations.MemberProhibition
import me.tbsten.katachi.konsist.declarations.Modifiers
import me.tbsten.katachi.konsist.declarations.PropertyAllow
import me.tbsten.katachi.konsist.declarations.PropertyKind
import me.tbsten.katachi.konsist.declarations.PropertyLine
import me.tbsten.katachi.konsist.declarations.TypeAliasAllow
import me.tbsten.katachi.konsist.declarations.TypeAliasKind
import me.tbsten.katachi.konsist.declarations.TypeAliasLine
import me.tbsten.katachi.konsist.declarations.TypeAllow
import me.tbsten.katachi.konsist.declarations.TypeKind
import me.tbsten.katachi.konsist.declarations.TypeLine

/**
 * What every `allow(...)` overload hands its values to: the lines of [list], allowed ones, or
 * prohibited ones for `not.allow(...)`.
 *
 * Each overload only says which kind it was given. What a line is made of is worked out here, in
 * one place: every value passed counts as used, each has to belong to [list]'s block, and the
 * candidates are every combination of one alternative of each [Modifiers] with each kind.
 */
internal class AllowWriter(
    private val list: DeclarationListBuilder,
    private val prohibited: Boolean,
) {
    fun type(modifiers: List<Modifiers>, kind: TypeKind, name: String, body: (ClassBodyScope.() -> Unit)?): TypeLine {
        val line = declare(modifiers, listOf(kind.word), kind.kinds.map { Shape(ModifierSet.EMPTY, it) }, name)
        if (body != null) line.writeBody(body)
        return TypeLine(line)
    }

    /** A fun interface: [kind] with the modifier `fun`, which keeps only an interface. */
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

    fun function(modifiers: List<Modifiers>, kind: FunctionKind, name: String): FunctionLine =
        FunctionLine(declare(modifiers, listOf(kind.word), listOf(Shape(ModifierSet.EMPTY, DeclarationKindName.Function)), name))

    fun property(modifiers: List<Modifiers>, kind: PropertyKind, name: String, type: String?): PropertyLine {
        val line = declare(modifiers, listOf(kind.word), kind.kinds.map { Shape(ModifierSet.EMPTY, it) }, name)
        if (type != null) line.writePropertyType(type)
        return PropertyLine(line)
    }

    fun typeAlias(modifiers: List<Modifiers>, kind: TypeAliasKind, name: String): TypeAliasLine =
        TypeAliasLine(declare(modifiers, listOf(kind.word), listOf(Shape(ModifierSet.EMPTY, DeclarationKindName.TypeAlias)), name))

    fun anythingElse(modifiers: List<Modifiers>, anything: AnythingElse) {
        val alternatives = alternativesOf(modifiers)
        use(modifiers.map { it.word } + anything.word) { renderAnythingElseLine(alternatives, prohibited) }
        list.addAnythingElse(alternatives, prohibited)
    }

    private fun declare(modifiers: List<Modifiers>, kinds: List<VocabularyWord>, kindShapes: List<Shape>, name: String): DeclarationLineBuilder {
        val shapes = alternativesOf(modifiers)
            .flatMap { set -> kindShapes.map { Shape(set.union(it.modifiers), it.kind) } }
            .distinct()
        use(modifiers.map { it.word } + kinds) { renderDeclaration(shapes, name, prohibited) }
        return list.declare(shapes, prohibited, name)
    }

    /**
     * Marks [words] as used, then refuses one of another block.
     *
     * @throws me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException when a word
     *   belongs to another block than [list]'s, [line] being how the line reads.
     */
    private fun use(words: List<VocabularyWord>, line: () -> String) {
        words.forEach { it.trace.use() }
        words.forEach { it.requireIn(list, line) }
    }

    /** Every combination of one alternative of each of [modifiers]: the empty set for none. */
    private fun alternativesOf(modifiers: List<Modifiers>): List<ModifierSet> =
        modifiers.fold(listOf(ModifierSet.EMPTY)) { sets, next ->
            sets.flatMap { set -> next.alternatives.map { set.union(it) } }
        }.distinct()

    override fun toString(): String = "AllowWriter($list, prohibited=$prohibited)"
}

/*
 * The writer behind each `allow(...)` interface. Kotlin has no internal member in an interface, so
 * the overloads reach it through these: every implementation of the interfaces is one of the three
 * classes below, which the sealed hierarchy lets `when` check without an `else`.
 */

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
