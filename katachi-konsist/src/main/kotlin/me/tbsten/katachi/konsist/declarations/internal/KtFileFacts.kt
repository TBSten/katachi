package me.tbsten.katachi.konsist.declarations.internal

// Only what is written in the file: no inferred type, inherited member or compiler-generated member.

internal class KtFileFacts(
    val path: String,
    val packageName: String?,
    val packageLine: Int?,
    val imports: List<ImportFact>,
    val declarations: List<DeclarationFact>,
) {
    override fun toString(): String = "KtFileFacts($path)"
}

internal class ImportFact(val path: String, val line: Int?) {
    override fun toString(): String = "ImportFact($path)"
}

internal class TypeText(val written: String, val canonical: String) {
    override fun toString(): String = written
}

// modifiers: of `vararg`, `noinline` and `crossinline`, those written. defaultValue: as written after `=`, or null with none.
internal class ParameterFact(
    val name: String,
    val type: TypeText,
    val modifiers: Set<String> = emptySet(),
    val defaultValue: String? = null,
) {
    override fun toString(): String = "$name: ${type.written}"
}

internal class SupertypeFact(val written: String, val withoutArguments: String, val canonical: String) {
    override fun toString(): String = written
}

// target: the use-site target as written (`get` of `@get:JvmName`). arguments: what is between the parentheses, spaces taken out.
internal class AnnotationFact(
    val simpleName: String,
    val qualifiedCandidates: Set<String>,
    val target: String? = null,
    val arguments: String? = null,
) {
    override fun toString(): String = "@$simpleName"
}

internal class DeclarationFact(
    val kind: DeclarationKindName,
    val name: String,
    val receiver: TypeText? = null,
    val modifiers: Set<ModifierName>,
    val annotations: List<AnnotationFact>,
    val supertypes: List<SupertypeFact>,
    val propertyType: TypeText?,
    val parameters: List<ParameterFact>?,
    val returnType: TypeText?,
    val returnTypeWritten: Boolean,
    val line: Int?,
    val members: List<DeclarationFact>?,
    val inConstructor: Boolean = false,
    val details: DetailFacts = DetailFacts.NONE,
) {
    override fun toString(): String = "DeclarationFact(${renderFact(this)})"
}

// What the details of a line (`.context(...)`, `.kdoc()`, ...) are asked against. Each is as written; nothing is inferred.
internal class DetailFacts(
    // Of `context(logger: Logger)`; a context receiver (`context(Logger)`) is named `_`.
    val contextParameters: List<ParameterFact> = emptyList(),
    val typeParameters: List<TypeParameterFact> = emptyList(),
    val hasKDoc: Boolean = false,
    // Of a function: how its body is written. null on any other declaration.
    val body: BodyKind? = null,
) {
    override fun toString(): String = "DetailFacts"

    companion object {
        val NONE: DetailFacts = DetailFacts()
    }
}

// modifiers: of `reified`, `in` and `out`, those written. bounds: after its `:` and in a `where` clause.
internal class TypeParameterFact(val name: String, val modifiers: Set<String>, val bounds: List<TypeText>) {
    override fun toString(): String = renderTypeParameter(this)
}

internal enum class BodyKind(val call: String, val label: String) {
    Expression("expressionBody", "an expression body"),
    Block("blockBody", "a block body"),
    None("noBody", "no body"),
}
