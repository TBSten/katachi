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

internal class ParameterFact(val name: String, val type: TypeText) {
    override fun toString(): String = "$name: ${type.written}"
}

internal class SupertypeFact(val written: String, val withoutArguments: String, val canonical: String) {
    override fun toString(): String = written
}

internal class AnnotationFact(val simpleName: String, val qualifiedCandidates: Set<String>) {
    override fun toString(): String = "@$simpleName"
}

internal class DeclarationFact(
    val kind: DeclarationKindName,
    val name: String,
    // Shown in a report only: a line's name is matched without it.
    val receiver: String? = null,
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
) {
    override fun toString(): String = "DeclarationFact(${renderFact(this)})"
}
