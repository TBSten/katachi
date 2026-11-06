package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.konsist.declarations.internal.AllowWriter
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName
import me.tbsten.katachi.konsist.declarations.internal.VocabularyWord
import me.tbsten.katachi.konsist.declarations.internal.DeclarationListBuilder
import me.tbsten.katachi.konsist.declarations.internal.FileBuilder

// These must stay in this package: Kotlin keeps a sealed type's direct subtypes in its own package.

internal abstract class DeclarationListScopeImpl(
    protected val list: DeclarationListBuilder,
) : DeclarationListScope,
    DeclarationVocabulary by VocabularyImpl(list) {
    val writer: AllowWriter = AllowWriter(list, prohibited = false)

    val requiredWriter: AllowWriter = AllowWriter(list, prohibited = false, required = true)

    override val fileName: String get() = list.session.input.fileName

    override fun captureValue(name: String): String = list.session.input.captureValue(name)

    override fun anyArgs(): AnyArgs = AnyArgs()
}

internal class KtFileContentScopeImpl(
    private val file: FileBuilder,
) : DeclarationListScopeImpl(file.declarations), KtFileContentScope {
    override val not: FileProhibition = FileProhibition(file)

    override val matchingDirectory: MatchingDirectory get() = MatchingDirectory()

    override fun `package`(pattern: String): PackageLine = PackageLine(file.declarePackage(pattern, prohibited = false))

    override fun `package`(directory: MatchingDirectory): PackageLine = PackageLine(file.declareMatchingDirectory())

    override fun `import`(pattern: String): ImportLine = ImportLine(file.declareImport(pattern, prohibited = false))

    override fun toString(): String = "KtFileContentScope(${file.session.input})"
}

internal class ClassBodyScopeImpl(
    members: DeclarationListBuilder,
) : DeclarationListScopeImpl(members), ClassBodyScope {
    override val not: Prohibition = MemberProhibition(members)

    override val entry: EntryKind get() = EntryKind(VocabularyWord.of(list) { DeclarationKindName.EnumEntry.keyword })

    override val constructor: ConstructorKind get() = ConstructorKind(VocabularyWord.of(list) { DeclarationKindName.Constructor.keyword })

    override val init: InitKind get() = InitKind(VocabularyWord.of(list) { DeclarationKindName.Init.keyword })

    override fun allow(kind: EntryKind, name: String): EntryLine = EntryLine(writer.member(emptyList(), kind.word, DeclarationKindName.EnumEntry, name))

    override fun allow(m1: Modifiers, kind: EntryKind, name: String): EntryLine =
        EntryLine(writer.member(listOf(m1), kind.word, DeclarationKindName.EnumEntry, name))

    override fun allow(kind: ConstructorKind): ConstructorLine = ConstructorLine(writer.member(emptyList(), kind.word, DeclarationKindName.Constructor, "*"))

    override fun allow(m1: Modifiers, kind: ConstructorKind): ConstructorLine =
        ConstructorLine(writer.member(listOf(m1), kind.word, DeclarationKindName.Constructor, "*"))

    override fun allow(kind: InitKind): InitLine = InitLine(writer.member(emptyList(), kind.word, DeclarationKindName.Init, "*"))

    override fun required(kind: EntryKind, name: String): EntryLine =
        EntryLine(requiredWriter.member(emptyList(), kind.word, DeclarationKindName.EnumEntry, name))

    override fun required(m1: Modifiers, kind: EntryKind, name: String): EntryLine =
        EntryLine(requiredWriter.member(listOf(m1), kind.word, DeclarationKindName.EnumEntry, name))

    override fun required(kind: ConstructorKind): ConstructorLine =
        ConstructorLine(requiredWriter.member(emptyList(), kind.word, DeclarationKindName.Constructor, "*"))

    override fun required(m1: Modifiers, kind: ConstructorKind): ConstructorLine =
        ConstructorLine(requiredWriter.member(listOf(m1), kind.word, DeclarationKindName.Constructor, "*"))

    override fun required(kind: InitKind): InitLine = InitLine(requiredWriter.member(emptyList(), kind.word, DeclarationKindName.Init, "*"))

    override fun toString(): String = "ClassBodyScope($list)"
}

internal class MemberProhibition(
    members: DeclarationListBuilder,
) : Prohibition {
    val writer: AllowWriter = AllowWriter(members, prohibited = true)

    override fun toString(): String = "not"
}
