package me.tbsten.katachi.konsist.declarations.internal

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.declaration.KoTypeAliasDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoBaseProvider
import com.lemonappdev.konsist.api.provider.KoDeclarationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoPathProvider
import com.lemonappdev.konsist.api.provider.KoTextProvider
import com.lemonappdev.konsist.api.provider.modifier.KoModifierProvider
import me.tbsten.katachi.konsist.internal.lineOf

/*
 * Konsist's parse of one file, written down as the facts the matching reads (`KtFileFacts`).
 *
 * Every query that has an `includeNested` is asked with `false`: Konsist's default is `true`,
 * which would list a nested class's members as if they were the file's own.
 */

/** The `ModifierName` of each Konsist modifier keyword. `final`, `vararg` and the like are not among them. */
private val MODIFIERS_BY_KEYWORD: Map<String, ModifierName> = ModifierName.entries.associateBy { it.keyword }

/** What a function with a block body, or no body, returns when it writes no return type. */
private const val UNIT: String = "Unit"

/** The facts of [file], whose path relative to the project is [path]. */
internal fun ktFileFactsOf(file: KoFileDeclaration, path: String): KtFileFacts {
    val packageName = file.packagee?.name?.takeIf { it.isNotEmpty() }
    val imports = file.imports.map { import ->
        ImportedName(path = import.name.removeSuffix(".*"), alias = import.alias?.name, isWildcard = import.isWildcard)
    }
    val reader = FactReader(packageName, imports)
    return KtFileFacts(
        path = path,
        packageName = packageName,
        packageLine = file.packagee?.let(::lineOfElement),
        imports = file.imports.zip(imports).map { (import, imported) ->
            ImportFact(path = if (imported.isWildcard) "${imported.path}.*" else imported.path, line = lineOfElement(import))
        },
        declarations = reader.declarationsOf(file),
    )
}

/** Reads declarations of one file, whose package and imports say what an annotation's name may stand for. */
private class FactReader(private val packageName: String?, private val imports: List<ImportedName>) {
    /** What [container] declares directly, in the order written. */
    fun declarationsOf(container: KoDeclarationProvider): List<DeclarationFact> =
        container.declarations(includeNested = false, includeLocal = false).mapNotNull(::factOf)

    private fun factOf(declaration: KoBaseDeclaration): DeclarationFact? = when (declaration) {
        is KoClassDeclaration -> typeFact(declaration, DeclarationKindName.Class)
        is KoInterfaceDeclaration -> typeFact(declaration, DeclarationKindName.Interface)
        is KoObjectDeclaration -> typeFact(declaration, DeclarationKindName.Object)
        is KoFunctionDeclaration -> functionFact(declaration)
        is KoPropertyDeclaration -> propertyFact(declaration)
        is KoTypeAliasDeclaration -> fact(declaration, DeclarationKindName.TypeAlias)
        else -> null
    }

    private fun typeFact(declaration: KoBaseDeclaration, kind: DeclarationKindName): DeclarationFact {
        val name = nameOf(declaration)
        val annotationTexts = (declaration as? KoAnnotationProvider)?.annotations.orEmpty().map { it.text }
        val text = (declaration as? KoTextProvider)?.text.orEmpty()
        return fact(
            declaration = declaration,
            kind = kind,
            supertypes = supertypesOf(headerTextOf(text, annotationTexts), name),
            members = (declaration as? KoDeclarationProvider)?.let(::declarationsOf).orEmpty(),
        )
    }

    private fun functionFact(declaration: KoFunctionDeclaration): DeclarationFact {
        val written = declaration.returnType?.text
        val returnType = when {
            written != null -> typeTextOf(written)
            declaration.hasExpressionBody -> null
            else -> typeTextOf(UNIT)
        }
        return fact(
            declaration = declaration,
            kind = DeclarationKindName.Function,
            parameterTypes = declaration.parameters.map { typeTextOf(it.type.text) },
            returnType = returnType,
            returnTypeWritten = written != null,
        )
    }

    private fun propertyFact(declaration: KoPropertyDeclaration): DeclarationFact = fact(
        declaration = declaration,
        kind = if (declaration.isVar) DeclarationKindName.Var else DeclarationKindName.Val,
        propertyType = declaration.type?.text?.let(::typeTextOf),
    )

    private fun fact(
        declaration: KoBaseDeclaration,
        kind: DeclarationKindName,
        supertypes: List<SupertypeFact> = emptyList(),
        propertyType: TypeText? = null,
        parameterTypes: List<TypeText>? = null,
        returnType: TypeText? = null,
        returnTypeWritten: Boolean = false,
        members: List<DeclarationFact>? = null,
    ): DeclarationFact = DeclarationFact(
        kind = kind,
        name = nameOf(declaration),
        modifiers = (declaration as? KoModifierProvider)?.modifiers.orEmpty()
            .mapNotNullTo(LinkedHashSet()) { MODIFIERS_BY_KEYWORD[it.type] },
        annotations = (declaration as? KoAnnotationProvider)?.annotations.orEmpty()
            .map { annotationFactOf(it.text, packageName, imports) },
        supertypes = supertypes,
        propertyType = propertyType,
        parameterTypes = parameterTypes,
        returnType = returnType,
        returnTypeWritten = returnTypeWritten,
        line = lineOfElement(declaration)?.plus(kDocLinesOf(declaration)),
        members = members,
    )
}

/** The name Konsist gives [declaration], without the backticks it may be written with. */
private fun nameOf(declaration: KoBaseDeclaration): String =
    (declaration as? KoNameProvider)?.name.orEmpty().removeSurrounding("`")

/** [text] as a written type: its spaces made single, and its canonical form beside it. */
internal fun typeTextOf(text: String): TypeText = TypeText(collapseSpaces(text), canonicalType(text))

/**
 * How many lines the KDoc [declaration] starts with takes: Konsist places a declaration where its
 * text starts, which is its KDoc, while a report means the line the declaration (its first
 * annotation, or its first modifier or keyword) is on.
 */
private fun kDocLinesOf(declaration: KoBaseDeclaration): Int {
    val text = (declaration as? KoTextProvider)?.text ?: return 0
    val rest = withoutLeadingComments(text)
    return text.substring(0, text.length - rest.length).count { it == '\n' }
}

/** The 1-based line of [element] in its own file, or `null` when Konsist cannot place it. */
private fun lineOfElement(element: KoBaseProvider): Int? {
    val path = (element as? KoPathProvider)?.path ?: return null
    return lineOf(element, path)
}
