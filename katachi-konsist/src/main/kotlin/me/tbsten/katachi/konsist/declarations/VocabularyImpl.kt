package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName
import me.tbsten.katachi.konsist.declarations.internal.DeclarationListBuilder
import me.tbsten.katachi.konsist.declarations.internal.ModifierName
import me.tbsten.katachi.konsist.declarations.internal.ModifierSet
import me.tbsten.katachi.konsist.declarations.internal.VocabularyWord
import me.tbsten.katachi.konsist.declarations.internal.annotationRule
import me.tbsten.katachi.konsist.declarations.internal.renderKinds
import me.tbsten.katachi.konsist.declarations.internal.renderModifierAlternatives

internal class VocabularyImpl(private val list: DeclarationListBuilder) : DeclarationVocabulary {
    override val public: Modifiers get() = modifier(ModifierName.Public)
    override val protected: Modifiers get() = modifier(ModifierName.Protected)
    override val private: Modifiers get() = modifier(ModifierName.Private)
    override val internal: Modifiers get() = modifier(ModifierName.Internal)
    override val expect: Modifiers get() = modifier(ModifierName.Expect)
    override val actual: Modifiers get() = modifier(ModifierName.Actual)
    override val abstract: Modifiers get() = modifier(ModifierName.Abstract)
    override val open: Modifiers get() = modifier(ModifierName.Open)
    override val sealed: Modifiers get() = modifier(ModifierName.Sealed)
    override val const: Modifiers get() = modifier(ModifierName.Const)
    override val external: Modifiers get() = modifier(ModifierName.External)
    override val final: Modifiers get() = modifier(ModifierName.Final)
    override val override: Modifiers get() = modifier(ModifierName.Override)
    override val lateinit: Modifiers get() = modifier(ModifierName.Lateinit)
    override val tailrec: Modifiers get() = modifier(ModifierName.Tailrec)
    override val suspend: Modifiers get() = modifier(ModifierName.Suspend)
    override val inner: Modifiers get() = modifier(ModifierName.Inner)
    override val enum: Modifiers get() = modifier(ModifierName.Enum)
    override val annotation: Modifiers get() = modifier(ModifierName.Annotation)
    override val companion: Modifiers get() = modifier(ModifierName.Companion)
    override val inline: Modifiers get() = modifier(ModifierName.Inline)
    override val value: Modifiers get() = modifier(ModifierName.Value)
    override val infix: Modifiers get() = modifier(ModifierName.Infix)
    override val operator: Modifiers get() = modifier(ModifierName.Operator)
    override val data: Modifiers get() = modifier(ModifierName.Data)

    override fun annotated(vararg names: String): Modifiers {
        if (names.isEmpty()) {
            throw KatachiKtFileContentLineException(
                problem = KtFileContentLineProblem.AnnotatedWithoutNames,
                line = "annotated()",
                declaredAt = DeclarationSite.capture(),
            )
        }
        val annotations = names.map { list.session.patterns.annotationRule(it) }
        return modifiers(listOf(ModifierSet.EMPTY.withAnnotations(annotations)))
    }

    override val `class`: TypeKind get() = typeKind(DeclarationKindName.Class)
    override val `interface`: TypeKind get() = typeKind(DeclarationKindName.Interface)
    override val `object`: TypeKind get() = typeKind(DeclarationKindName.Object)
    override val type: TypeKind
        get() = typeKind(DeclarationKindName.Class, DeclarationKindName.Interface, DeclarationKindName.Object)
    override val `fun`: FunctionKind get() = FunctionKind(word(DeclarationKindName.Function.keyword))
    override val `val`: PropertyKind get() = propertyKind(DeclarationKindName.Val)
    override val `var`: PropertyKind get() = propertyKind(DeclarationKindName.Var)
    override val `typealias`: TypeAliasKind get() = TypeAliasKind(word(DeclarationKindName.TypeAlias.keyword))
    override val anythingElse: AnythingElse get() = AnythingElse(word("anythingElse"))

    private fun modifier(modifier: ModifierName): Modifiers = modifiers(listOf(ModifierSet.EMPTY + modifier))

    private fun modifiers(alternatives: List<ModifierSet>): Modifiers =
        Modifiers(alternatives, VocabularyWord.of(list) { renderModifierAlternatives(alternatives) })

    private fun typeKind(vararg kinds: DeclarationKindName): TypeKind = kinds.toList().let { TypeKind(it, word(renderKinds(it))) }

    private fun propertyKind(kind: DeclarationKindName): PropertyKind = PropertyKind(listOf(kind), word(kind.keyword))

    // TODO: DeclarationSite.capture() walks the stack once per value; keep only the line's own site if a benchmark shows it.
    private fun word(text: String): VocabularyWord = VocabularyWord.of(list) { text }

    override fun toString(): String = "VocabularyImpl($list)"
}
