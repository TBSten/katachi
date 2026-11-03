package me.tbsten.katachi.konsist.declarations.internal

// Nothing is resolved: an explicit import (or its alias) gives that name only, hiding the same name in the package,
// in a star import and in the default imports.

private val DEFAULT_IMPORTS: List<String> = listOf(
    "kotlin",
    "kotlin.annotation",
    "kotlin.collections",
    "kotlin.comparisons",
    "kotlin.io",
    "kotlin.ranges",
    "kotlin.sequences",
    "kotlin.text",
    "kotlin.jvm",
    "java.lang",
)

internal class ImportedName(val path: String, val alias: String?, val isWildcard: Boolean) {
    override fun toString(): String = "ImportedName($path)"
}

internal fun annotationFactOf(annotationText: String, packageName: String?, imports: List<ImportedName>): AnnotationFact {
    val written = writtenAnnotationName(annotationText)
    val simpleName = written.substringAfterLast('.')
    if ('.' in written) return AnnotationFact(simpleName, setOf(written))
    val explicit = imports.filter { import ->
        !import.isWildcard && (import.alias ?: import.path.substringAfterLast('.')) == written
    }
    if (explicit.isNotEmpty()) return AnnotationFact(simpleName, explicit.mapTo(LinkedHashSet()) { it.path })
    val candidates = buildSet {
        add(if (packageName.isNullOrEmpty()) written else "$packageName.$written")
        for (import in imports) if (import.isWildcard) add("${import.path}.$written")
        for (defaultPackage in DEFAULT_IMPORTS) add("$defaultPackage.$written")
    }
    return AnnotationFact(simpleName, candidates)
}

internal fun writtenAnnotationName(annotationText: String): String {
    val withoutAt = annotationText.trim().removePrefix("@")
    val withoutTarget = withoutAt.substringBefore('(').let { head ->
        if (':' in head) withoutAt.substringAfter(':') else withoutAt
    }
    return withoutTarget.takeWhile { !it.isWhitespace() && it != '(' && it != '<' }.trim().removeSurrounding("`")
}
