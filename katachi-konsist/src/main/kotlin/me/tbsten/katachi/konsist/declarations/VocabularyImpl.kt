package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName
import me.tbsten.katachi.konsist.declarations.internal.DeclarationListBuilder
import me.tbsten.katachi.konsist.declarations.internal.ModifierName
import me.tbsten.katachi.konsist.declarations.internal.ModifierTrace
import me.tbsten.katachi.konsist.declarations.internal.ModifierSet
import me.tbsten.katachi.konsist.declarations.internal.Shape
import me.tbsten.katachi.konsist.declarations.internal.annotationRule

/**
 * The one implementation of [DeclarationVocabulary], which the scopes, [Modifiers] and
 * [Prohibition] all delegate to: each word adds to the [alternatives] written so far, keeping
 * whether the line is [prohibited] and which list it goes to ([sink]).
 */
internal class VocabularyImpl(
    private val alternatives: List<ModifierSet>,
    private val prohibited: Boolean,
    private val sink: DeclarationListBuilder,
    /** The value these words are chained after, which they build on; `null` at the start of a line. */
    private val after: ModifierTrace? = null,
) : DeclarationVocabulary {
    override val public: Modifiers get() = adding(ModifierName.Public)
    override val protected: Modifiers get() = adding(ModifierName.Protected)
    override val private: Modifiers get() = adding(ModifierName.Private)
    override val internal: Modifiers get() = adding(ModifierName.Internal)
    override val expect: Modifiers get() = adding(ModifierName.Expect)
    override val actual: Modifiers get() = adding(ModifierName.Actual)
    override val abstract: Modifiers get() = adding(ModifierName.Abstract)
    override val open: Modifiers get() = adding(ModifierName.Open)
    override val sealed: Modifiers get() = adding(ModifierName.Sealed)
    override val const: Modifiers get() = adding(ModifierName.Const)
    override val external: Modifiers get() = adding(ModifierName.External)
    override val override: Modifiers get() = adding(ModifierName.Override)
    override val lateinit: Modifiers get() = adding(ModifierName.Lateinit)
    override val tailrec: Modifiers get() = adding(ModifierName.Tailrec)
    override val suspend: Modifiers get() = adding(ModifierName.Suspend)
    override val inner: Modifiers get() = adding(ModifierName.Inner)
    override val enum: Modifiers get() = adding(ModifierName.Enum)
    override val annotation: Modifiers get() = adding(ModifierName.Annotation)
    override val companion: Modifiers get() = adding(ModifierName.Companion)
    override val inline: Modifiers get() = adding(ModifierName.Inline)
    override val value: Modifiers get() = adding(ModifierName.Value)
    override val infix: Modifiers get() = adding(ModifierName.Infix)
    override val operator: Modifiers get() = adding(ModifierName.Operator)
    override val data: Modifiers get() = adding(ModifierName.Data)

    override fun annotated(vararg names: String): Modifiers {
        if (names.isEmpty()) {
            throw KatachiKtFileContentLineException(
                problem = KtFileContentLineProblem.AnnotatedWithoutNames,
                line = "annotated()",
                declaredAt = DeclarationSite.capture(),
            )
        }
        val annotations = names.map { sink.session.patterns.annotationRule(it) }
        return modifiers(alternatives.map { it.withAnnotations(annotations) })
    }

    // TODO: a line that chains only a kind onto modifiers (`annotated("X").`class``, no name) counts as
    //  used and is still dropped silently; track it on the kinds too and use the trace when a name is given.
    override val `class`: TypeKind get() = typeKind(DeclarationKindName.Class)
    override val `interface`: TypeKind get() = typeKind(DeclarationKindName.Interface)
    override val `object`: TypeKind get() = typeKind(DeclarationKindName.Object)
    override val type: TypeKind
        get() = typeKind(DeclarationKindName.Class, DeclarationKindName.Interface, DeclarationKindName.Object)
    override val `fun`: FunctionKind get() = built { FunctionKind(alternatives, prohibited, sink) }
    override val `val`: PropertyKind get() = built { PropertyKind(shapesOf(DeclarationKindName.Val), prohibited, sink) }
    override val `var`: PropertyKind get() = built { PropertyKind(shapesOf(DeclarationKindName.Var), prohibited, sink) }
    override val `typealias`: TypeAliasKind get() = built { TypeAliasKind(alternatives, prohibited, sink) }

    private fun adding(modifier: ModifierName): Modifiers = modifiers(alternatives.map { it + modifier })

    /** A value for [next], which builds on [after] and is itself kept to be asked about when the run ends. */
    private fun modifiers(next: List<ModifierSet>): Modifiers {
        after?.use()
        // TODO: DeclarationSite.capture() walks the stack once per chain; reuse the line's own site if a benchmark shows it.
        val trace = ModifierTrace(next, prohibited, after?.declaredAt ?: DeclarationSite.capture())
        return Modifiers(next, prohibited, sink, sink.session.modifiers.track(trace))
    }

    /** [create], after marking the value these words are chained after as built on. */
    private fun <T> built(create: () -> T): T {
        after?.use()
        return create()
    }

    private fun typeKind(vararg kinds: DeclarationKindName): TypeKind = built { TypeKind(shapesOf(*kinds), prohibited, sink) }

    private fun shapesOf(vararg kinds: DeclarationKindName): List<Shape> =
        alternatives.flatMap { set -> kinds.map { Shape(set, it) } }

    override fun toString(): String = "VocabularyImpl($alternatives, prohibited=$prohibited)"
}
