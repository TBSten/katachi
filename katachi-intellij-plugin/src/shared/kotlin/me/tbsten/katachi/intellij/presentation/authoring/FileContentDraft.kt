package me.tbsten.katachi.intellij.presentation.authoring

/**
 * One top-level declaration of a file a role holds, as its Kotlin source writes it: the [modifiers] by the name the
 * `ktFileContent { }` vocabulary has (`private`, `data`, `sealed`, ...), the [kind] as a line writes it (`class`, `interface`,
 * `object`, `fun`, `val`, `var`, `typealias`) and whether it has [hasMembers] a `{ }` of its own to allow.
 */
internal data class SampleDeclaration(
    val modifiers: List<String>,
    val kind: String,
    val name: String,
    val hasMembers: Boolean,
)

/** What a `ktFileContent { }` draft reads of one file: its name without the extension, package, imports and declarations. */
internal data class SampleFile(
    val baseName: String,
    val packageName: String,
    val imports: List<String>,
    val declarations: List<SampleDeclaration>,
)

/**
 * The lines of a `ktFileContent { }` that the files a role holds already pass: the `package` pattern, the `import` lines and
 * the declaration lines (ide-live-template-from-definition). Pure text, so that `katachifilecontent` only has to put it in
 * the editor; what a draft leaves out is on purpose: annotations (a line does not ask about the ones it does not write),
 * parameters and bodies.
 *
 * ```kotlin
 * val draft = FileContentDrafts.of(files)
 * draft.packagePattern       // "com.example.service"
 * draft.importLines          // [`import`("kotlinx.coroutines.**")]
 * draft.declarationLines     // [required(`class`, fileName) { allow(anythingElse) }]
 * ```
 */
internal data class FileContentDraft(
    val packagePattern: String,
    val importLines: List<String>,
    val declarationLines: List<String>,
)

internal object FileContentDrafts {
    /** The imports written one by one up to this many; more are grouped under their first two names (`kotlinx.coroutines.**`). */
    const val IMPORTS_LISTED: Int = 8

    /** The import lines kept at most; more groups fall to their first name (`kotlinx.**`). */
    const val IMPORT_LINES_MAX: Int = 12

    /** The declaration lines named one by one up to this many; the others are written for any name (`"*"`). */
    const val DECLARATIONS_NAMED: Int = 12

    /** `null` when there is no file to learn from. */
    fun of(files: List<SampleFile>): FileContentDraft? {
        if (files.isEmpty()) return null
        return FileContentDraft(
            packagePattern = packagePatternOf(files.map { it.packageName }.distinct()),
            importLines = importLinesOf(files.flatMap { it.imports }.filter(::isWritable).distinct().sorted()),
            declarationLines = declarationLinesOf(files),
        )
    }

    /** One package as it is; several as the names they share and `**` (`com.example.**`), or `**` when they share none. */
    fun packagePatternOf(packageNames: List<String>): String {
        val packages = packageNames.distinct()
        val named = packages.filter { it.isNotEmpty() }
        if (named.isEmpty() || named.size != packages.size) return "**"
        if (named.size == 1) return named.single()
        val levels = named.map { it.split('.') }
        val shared = levels.first().indices.takeWhile { i -> levels.all { it.size > i && it[i] == levels.first()[i] } }.size
        return if (shared == 0) "**" else levels.first().take(shared).joinToString(".") + ".**"
    }

    private fun importLinesOf(imports: List<String>): List<String> {
        if (imports.isEmpty()) return emptyList()
        val patterns = if (imports.size <= IMPORTS_LISTED) imports else grouped(imports, 2).let { if (it.size > IMPORT_LINES_MAX) grouped(imports, 1) else it }
        return patterns.map { "`import`(\"$it\")" }
    }

    /** [imports] cut to their first [levels] names and `**`; one with no more names than that is kept as it is. */
    private fun grouped(imports: List<String>, levels: Int): List<String> = imports.map { path ->
        val names = path.split('.')
        if (names.size <= levels) path else names.take(levels).joinToString(".") + ".**"
    }.distinct().sorted()

    /** Backquotes and `$` would need escaping in the string a line is written with; such an import is left out of the draft. */
    private fun isWritable(path: String): Boolean = path.isNotBlank() && '`' !in path && '$' !in path && '"' !in path

    private fun declarationLinesOf(files: List<SampleFile>): List<String> {
        val perFile = files.map { file -> file.declarations.mapNotNull { lineOf(it, file.baseName) } }
        val named = perFile.flatten().map { it.name }.distinct().size
        // More names than a reader can take in: the ones that are not the file's own are written for any name.
        val lines = if (named > DECLARATIONS_NAMED) perFile.map { each -> each.map { if (it.name == FILE_NAME) it else it.copy(name = "\"*\"") } } else perFile
        // A line every file has, and named after the file, is required; the others are only allowed.
        val inEveryFile = lines.map { each -> each.map { it.key }.toSet() }.reduce { a, b -> a intersect b }
        return lines.flatten().groupBy { it.key }.map { (key, same) ->
            val line = same.first().copy(hasMembers = same.any { it.hasMembers })
            line.render(if (key in inEveryFile && line.name == FILE_NAME) "required" else "allow")
        }
    }

    private fun lineOf(declaration: SampleDeclaration, baseName: String): Line? {
        if (declaration.kind !in KINDS || declaration.name.isEmpty() || '`' in declaration.name || '$' in declaration.name) return null
        val modifiers = declaration.modifiers.filter { it in VOCABULARY }.filterNot { it == "public" || it == "final" }.sortedBy { VOCABULARY.indexOf(it) }
        val name = if (declaration.name == baseName) FILE_NAME else "\"${declaration.name}\""
        val members = declaration.hasMembers && declaration.kind in TYPE_KINDS
        return Line(modifiers, declaration.kind, name, members)
    }

    private const val FILE_NAME = "fileName"

    private data class Line(val modifiers: List<String>, val kind: String, val name: String, val hasMembers: Boolean) {
        /** What tells two lines apart for the merge: the same declaration of two files is one line, with members when either has. */
        val key: String get() = "${modifiers.joinToString(",")}|$kind|$name"

        fun render(verb: String): String {
            val words = modifiers + backquoted(kind) + name
            val head = "$verb(${words.joinToString(", ")})"
            return if (hasMembers) "$head {\n    allow(anythingElse)\n}" else head
        }
    }

    /** Every kind is a Kotlin keyword, which the vocabulary has as a value of the same name (`typealias` included). */
    private fun backquoted(kind: String): String = "`$kind`"

    private val KINDS: Set<String> = setOf("class", "interface", "object", "fun", "val", "var", "typealias")
    private val TYPE_KINDS: Set<String> = setOf("class", "interface", "object")

    /** The modifiers `DeclarationVocabulary` has a value for, in the order Kotlin writes them. */
    private val VOCABULARY: List<String> = listOf(
        "expect", "actual", "public", "protected", "private", "internal", "abstract", "open", "sealed", "final", "const", "external",
        "override", "lateinit", "tailrec", "suspend", "inner", "enum", "annotation", "companion", "inline", "value", "infix", "operator", "data",
    )
}
