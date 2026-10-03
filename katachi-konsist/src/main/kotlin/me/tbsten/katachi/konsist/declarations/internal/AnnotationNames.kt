package me.tbsten.katachi.konsist.declarations.internal

/*
 * Which full names an annotation written in a file may stand for, so that
 * `annotated("androidx.compose.runtime.Composable")` matches `@Composable` as well as
 * `@androidx.compose.runtime.Composable`. Read off the file's own imports and package: nothing is
 * resolved, so every name the annotation could be is a candidate.
 */

/**
 * The packages Kotlin imports into every file, whose annotations are written without an import:
 * `@JvmInline` is `kotlin.jvm.JvmInline`.
 */
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

/** One import as the candidates need it: its path, and the alias it is written with, if any. */
internal class ImportedName(val path: String, val alias: String?, val isWildcard: Boolean) {
    override fun toString(): String = "ImportedName($path)"
}

/**
 * The annotation written as [annotationText] (`@Composable`, `@get:JvmName("x")`), in a file whose
 * package is [packageName] and whose imports are [imports].
 */
internal fun annotationFactOf(annotationText: String, packageName: String?, imports: List<ImportedName>): AnnotationFact {
    val written = writtenAnnotationName(annotationText)
    val simpleName = written.substringAfterLast('.')
    if ('.' in written) return AnnotationFact(simpleName, setOf(written))
    val candidates = buildSet {
        for (import in imports) {
            when {
                import.isWildcard -> add("${import.path}.$written")
                import.alias == written -> add(import.path)
                import.alias == null && import.path.substringAfterLast('.') == written -> add(import.path)
            }
        }
        add(if (packageName.isNullOrEmpty()) written else "$packageName.$written")
        for (defaultPackage in DEFAULT_IMPORTS) add("$defaultPackage.$written")
    }
    return AnnotationFact(simpleName, candidates)
}

/**
 * The name an annotation is written with: without its `@`, without a use-site target (`get:`),
 * and without its arguments or type arguments.
 */
internal fun writtenAnnotationName(annotationText: String): String {
    val withoutAt = annotationText.trim().removePrefix("@")
    val withoutTarget = withoutAt.substringBefore('(').let { head ->
        if (':' in head) withoutAt.substringAfter(':') else withoutAt
    }
    return withoutTarget.takeWhile { !it.isWhitespace() && it != '(' && it != '<' }.trim().removeSurrounding("`")
}
