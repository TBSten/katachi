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
import com.lemonappdev.konsist.api.provider.KoIsConstructorDefinedProvider
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
    val reader = FactReader(packageName, imports, file.text)
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

private class FactReader(
    private val packageName: String?,
    private val imports: List<ImportedName>,
    private val fileText: String,
) {
    private val fileCode: String? by lazy { if (CONTEXT_KEYWORD in fileText) codeOnly(fileText) else null }

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
            receiver = declaration.receiverType?.text,
            parameters = declaration.parameters.map { ParameterFact(it.name.removeSurrounding("`"), typeTextOf(it.type.text)) },
            returnType = returnType,
            returnTypeWritten = written != null,
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
        return fact(
            declaration = declaration,
            kind = if (declaration.isVar) DeclarationKindName.Var else DeclarationKindName.Val,
            receiver = declaration.receiverType?.text,
            propertyType = written?.let(::typeTextOf),
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
    ): DeclarationFact {
        val line = lineOfElement(declaration)
        val beforeContext = line?.let { writtenBeforeContextOf(declaration, it) }
        val annotationTexts = beforeContext?.annotationTexts.orEmpty() +
            (declaration as? KoAnnotationProvider)?.annotations.orEmpty().map { it.text }
        return DeclarationFact(
            kind = kind,
            name = nameOf(declaration),
            receiver = receiver?.let(::collapseSpaces),
            modifiers = (declaration as? KoModifierProvider)?.modifiers.orEmpty()
                .mapNotNullTo(LinkedHashSet()) { MODIFIERS_BY_KEYWORD[it.type] },
            annotations = annotationTexts.map { annotationFactOf(it, packageName, imports) },
            supertypes = supertypes,
            propertyType = propertyType,
            parameters = parameters,
            returnType = returnType,
            returnTypeWritten = returnTypeWritten,
            line = beforeContext?.let { lineAt(fileText, it.start) } ?: line?.plus(kDocLinesOf(declaration)),
            members = members,
            inConstructor = (declaration as? KoIsConstructorDefinedProvider)?.isConstructorDefined == true,
        )
    }

    private fun writtenBeforeContextOf(declaration: KoBaseDeclaration, line: Int): WrittenBeforeContext? {
        val code = fileCode ?: return null
        val text = (declaration as? KoTextProvider)?.text ?: return null
        val start = fileText.indexOf(text, startOfLine(fileText, line)).takeIf { it >= 0 } ?: return null
        return writtenBeforeContext(fileText, code, start)
    }
}

private const val CONTEXT_KEYWORD: String = "context"

private fun startOfLine(text: String, line: Int): Int {
    var offset = 0
    repeat(line - 1) {
        val next = text.indexOf('\n', offset)
        if (next < 0) return offset
        offset = next + 1
    }
    return offset
}

private fun lineAt(text: String, offset: Int): Int = 1 + (0 until offset).count { text[it] == '\n' }

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
