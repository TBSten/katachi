package me.tbsten.katachi.konsist.declarations.internal

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoEnumConstantDeclaration
import com.lemonappdev.konsist.api.declaration.KoInitBlockDeclaration
import com.lemonappdev.konsist.api.declaration.KoSecondaryConstructorDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.declaration.KoTypeAliasDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoBaseProvider
import com.lemonappdev.konsist.api.provider.KoDeclarationProvider
import com.lemonappdev.konsist.api.provider.KoIsConstructorDefinedProvider
import com.lemonappdev.konsist.api.provider.KoKDocProvider
import com.lemonappdev.konsist.api.provider.KoTypeParameterProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoPathProvider
import com.lemonappdev.konsist.api.provider.KoTextProvider
import com.lemonappdev.konsist.api.provider.modifier.KoModifierProvider
import com.lemonappdev.konsist.core.util.KotlinFileParser
import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.konsist.internal.lineOf

// Every query with an `includeNested` is asked with `false`: Konsist's default is `true`, which lists a nested class's members as the file's own.

private val MODIFIERS_BY_KEYWORD: Map<String, ModifierName> = ModifierName.entries.associateBy { it.keyword }

private const val UNIT: String = "Unit"

// A file with a byte order mark is read again without it: Konsist reads no package or import from one, which would let a prohibited import through unseen.
internal fun ktFileFactsOf(parsed: KoFileDeclaration, path: String): KtFileFacts {
    val file = if (parsed.text.startsWith(BYTE_ORDER_MARK)) withoutByteOrderMark(parsed) else parsed
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
        fileAnnotations = file.annotations.map { annotation ->
            FileAnnotationFact(annotationFactOf(annotation.text, packageName, imports), collapseSpaces(annotation.text), lineOfElement(annotation))
        },
    )
}

private class FactReader(
    private val packageName: String?,
    private val imports: List<ImportedName>,
) {
    fun declarationsOf(container: KoDeclarationProvider): List<DeclarationFact> =
        container.declarations(includeNested = false, includeLocal = false).mapNotNull(::factOf)

    private fun factOf(declaration: KoBaseDeclaration): DeclarationFact? = when (declaration) {
        is KoClassDeclaration -> typeFact(declaration, DeclarationKindName.Class)
        is KoInterfaceDeclaration -> typeFact(declaration, DeclarationKindName.Interface)
        is KoObjectDeclaration -> typeFact(declaration, DeclarationKindName.Object)
        is KoFunctionDeclaration -> functionFact(declaration)
        is KoPropertyDeclaration -> propertyFact(declaration)
        is KoTypeAliasDeclaration -> fact(declaration, DeclarationKindName.TypeAlias, propertyType = typeTextOf(declaration.type.text))
        is KoEnumConstantDeclaration -> fact(declaration, DeclarationKindName.EnumEntry)
        is KoSecondaryConstructorDeclaration -> fact(
            declaration,
            DeclarationKindName.Constructor,
            parameters = declaration.parameters.map(::parameterFactOf),
            name = DeclarationKindName.Constructor.keyword,
        )
        is KoInitBlockDeclaration -> fact(declaration, DeclarationKindName.Init, name = DeclarationKindName.Init.keyword)
        else -> null
    }

    private fun typeFact(declaration: KoBaseDeclaration, kind: DeclarationKindName): DeclarationFact {
        val name = nameOf(declaration)
        val annotationTexts = (declaration as? KoAnnotationProvider)?.annotations.orEmpty().map { it.text }
        val text = (declaration as? KoTextProvider)?.text.orEmpty()
        val constructor = (declaration as? KoClassDeclaration)?.let { type ->
            type.primaryConstructor?.let { written ->
                ConstructorFact(modifiersOf(written), written.parameters.map(::parameterFactOf))
            } ?: ConstructorFact(emptySet(), emptyList())
        }
        return fact(
            declaration = declaration,
            kind = kind,
            supertypes = supertypesOf(headerTextOf(text, annotationTexts), name),
            members = (declaration as? KoDeclarationProvider)?.let(::declarationsOf).orEmpty(),
            details = detailsOf(declaration, text, annotationTexts, primaryConstructor = constructor),
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
            receiver = declaration.receiverType?.text,
            parameters = declaration.parameters.map(::parameterFactOf),
            returnType = returnType,
            returnTypeWritten = written != null,
            details = detailsOf(
                declaration,
                declaration.text,
                declaration.annotations.map { it.text },
                body = when {
                    declaration.hasExpressionBody -> BodyKind.Expression
                    declaration.hasBlockBody -> BodyKind.Block
                    else -> BodyKind.None
                },
            ),
        )
    }

    private fun propertyFact(declaration: KoPropertyDeclaration): DeclarationFact {
        // Konsist's type of an extension property is its receiver, so that one is read off the text.
        val written = if (declaration.isExtension) {
            val annotationTexts = declaration.annotations.map { it.text }
            extensionPropertyTypeOf(headerTextOf(declaration.text, annotationTexts), nameOf(declaration))
        } else {
            declaration.type?.text
        }
        val backingField = explicitBackingFieldOf(declaration.text)
        val property = PropertyDetailFacts(
            hasGetter = declaration.hasGetter,
            hasSetter = declaration.hasSetter,
            setterModifiers = declaration.setter?.let(::modifiersOf).orEmpty(),
            delegate = declaration.delegateName,
            hasBackingField = backingField != null,
            backingFieldType = backingField?.takeIf { it.isNotEmpty() }?.let(::typeTextOf),
        )
        return fact(
            declaration = declaration,
            kind = if (declaration.isVar) DeclarationKindName.Var else DeclarationKindName.Val,
            receiver = declaration.receiverType?.text,
            propertyType = written?.let(::typeTextOf),
            details = detailsOf(declaration, declaration.text, declaration.annotations.map { it.text }, property = property),
        )
    }

    private fun fact(
        declaration: KoBaseDeclaration,
        kind: DeclarationKindName,
        receiver: String? = null,
        supertypes: List<SupertypeFact> = emptyList(),
        propertyType: TypeText? = null,
        parameters: List<ParameterFact>? = null,
        returnType: TypeText? = null,
        returnTypeWritten: Boolean = false,
        members: List<DeclarationFact>? = null,
        name: String = nameOf(declaration),
        details: DetailFacts = detailsOf(declaration, (declaration as? KoTextProvider)?.text.orEmpty(), emptyList()),
    ): DeclarationFact {
        val line = lineOfElement(declaration)
        val annotationTexts = (declaration as? KoAnnotationProvider)?.annotations.orEmpty().map { it.text }
        return DeclarationFact(
            kind = kind,
            name = name,
            receiver = receiver?.let { typeTextOf(withoutOuterParentheses(it.trim())) },
            modifiers = modifiersOf(declaration),
            annotations = annotationTexts.map { annotationFactOf(it, packageName, imports) },
            supertypes = supertypes,
            propertyType = propertyType,
            parameters = parameters,
            returnType = returnType,
            returnTypeWritten = returnTypeWritten,
            line = line?.plus(kDocLinesOf(declaration)),
            members = members,
            inConstructor = (declaration as? KoIsConstructorDefinedProvider)?.isConstructorDefined == true,
            details = details,
        )
    }

    // Context parameters are read off the text, as Konsist 0.18 has no API for them (1.0 has `contextParameters`).
    private fun detailsOf(
        declaration: KoBaseDeclaration,
        text: String,
        annotationTexts: List<String>,
        body: BodyKind? = null,
        property: PropertyDetailFacts? = null,
        primaryConstructor: ConstructorFact? = null,
    ): DetailFacts = DetailFacts(
        contextParameters = contextParametersOf(headerTextOf(text, annotationTexts)),
        typeParameters = (declaration as? KoTypeParameterProvider)?.typeParameters.orEmpty().map { parameter ->
            TypeParameterFact(
                // Konsist's name of a type parameter is its whole text (`reified T : Any`).
                name = parameter.name.split(' ').filterNot { it in TYPE_PARAMETER_WORDS }.joinToString(" ")
                    .substringBefore(':').trim().removeSurrounding("`"),
                modifiers = parameter.modifiers.map { it.type }.filterTo(LinkedHashSet()) { it in TYPE_PARAMETER_WORDS },
                bounds = parameter.upperBounds.map { typeTextOf(it.text) },
            )
        },
        hasKDoc = (declaration as? KoKDocProvider)?.hasKDoc == true,
        body = body,
        property = property,
        primaryConstructor = primaryConstructor,
    )
}

private val TYPE_PARAMETER_WORDS: Set<String> = setOf("reified", "in", "out")

private fun modifiersOf(declaration: Any): Set<ModifierName> =
    (declaration as? KoModifierProvider)?.modifiers.orEmpty().mapNotNullTo(LinkedHashSet()) { MODIFIERS_BY_KEYWORD[it.type] }

internal fun parameterFactOf(parameter: KoParameterDeclaration): ParameterFact = ParameterFact(
    name = parameter.name.removeSurrounding("`"),
    type = typeTextOf(parameter.type.text),
    modifiers = buildSet {
        if (parameter.isVal) add("val")
        if (parameter.isVar) add("var")
        if (parameter.hasVarArgModifier) add("vararg")
        if (parameter.hasNoInlineModifier) add("noinline")
        if (parameter.hasCrossInlineModifier) add("crossinline")
    },
    defaultValue = parameter.defaultValue?.let(::collapseSpaces),
)

private const val BYTE_ORDER_MARK: Char = '\uFEFF'

// Parsed from a copy in a directory of its own; lines do not move, as the mark is on the first one.
private fun withoutByteOrderMark(file: KoFileDeclaration): KoFileDeclaration {
    val directory = Files.createTempDirectory("katachi-bom").toFile()
    try {
        val copy = File(directory, File(file.path).name)
        copy.writeText(file.text.removePrefix(BYTE_ORDER_MARK.toString()))
        return KotlinFileParser.getKoFile(copy)
    } finally {
        directory.deleteRecursively()
    }
}

private fun nameOf(declaration: KoBaseDeclaration): String =
    (declaration as? KoNameProvider)?.name.orEmpty().removeSurrounding("`")

internal fun typeTextOf(text: String): TypeText = TypeText(collapseSpaces(text), canonicalType(text))

// Konsist places a declaration where its text starts (its KDoc), while a report means the line of its first annotation, modifier or keyword.
private fun kDocLinesOf(declaration: KoBaseDeclaration): Int {
    val text = (declaration as? KoTextProvider)?.text ?: return 0
    val rest = withoutLeadingComments(text)
    return text.substring(0, text.length - rest.length).count { it == '\n' }
}

private fun lineOfElement(element: KoBaseProvider): Int? {
    val path = (element as? KoPathProvider)?.path ?: return null
    return lineOf(element, path)
}
