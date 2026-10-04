package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.konsist.declarations.internal.DeclarationListBuilder
import me.tbsten.katachi.konsist.declarations.internal.FileBuilder
import me.tbsten.katachi.konsist.declarations.internal.ModifierSet

/*
 * The scopes a `ktFileContent { }` block and the `{ }` of its class lines run in. They sit next to
 * the sealed interfaces they implement because Kotlin keeps a sealed type's direct subtypes in its
 * own package.
 */

/** What both scopes share: the vocabulary, the file's values, and the lines of one declaration list. */
internal abstract class DeclarationListScopeImpl(
    protected val list: DeclarationListBuilder,
) : DeclarationListScope,
    DeclarationVocabulary by VocabularyImpl(listOf(ModifierSet.EMPTY), prohibited = false, sink = list) {
    override val fileName: String get() = list.session.input.fileName

    override fun captureValue(name: String): String = list.session.input.captureValue(name)

    override fun anythingElse() {
        list.addAnythingElse(listOf(ModifierSet.EMPTY), prohibited = false)
    }

    override fun anyArgs(): AnyArgs = AnyArgs()
}

/** The receiver of `ktFileContent { }` for one run of the block. */
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

/** The receiver of the `{ }` of one class line. */
internal class ClassBodyScopeImpl(
    members: DeclarationListBuilder,
) : DeclarationListScopeImpl(members), ClassBodyScope {
    override val not: Prohibition = MemberProhibition(members)

    override fun toString(): String = "ClassBodyScope($list)"
}

/** `not.` inside the `{ }` of a class: members only, no import or package. */
internal class MemberProhibition(
    members: DeclarationListBuilder,
) : Prohibition,
    DeclarationVocabulary by VocabularyImpl(listOf(ModifierSet.EMPTY), prohibited = true, sink = members) {
    override fun toString(): String = "not"
}
