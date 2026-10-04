package me.tbsten.katachi.test.konsist.declarations

import kotlin.random.Random

/*
 * Walking and editing a generated case: the lists of declarations it holds with the lines that
 * govern each, one declaration changed in place, and the smaller cases the shrinker tries.
 */

/** [case]'s source judged against its allow list, one line per failure as `judged` writes it. */
internal fun judgedCase(case: GenCase, noise: Random? = null): List<String> = judged(sourceOf(case, noise), case.path) { writeCase(case) }

/** The declaration (or import, or package) a judged line is about: `Sample.state`, `import a.B`, `package (none)`. */
internal fun keyOf(judgedLine: String): String = judgedLine.substringBefore(" | ").substringBefore(" (line ")

/** A judged line without its line numbers, for comparing two runs whose lines moved. */
internal fun withoutLineNumbers(judgedLine: String): String = judgedLine.replace(Regex(""" \(line \d+\)"""), "")

/**
 * One list of declarations in a case: the file's (an empty [path]) or the members of the declaration
 * [path] leads to, with the lines that govern it -- `null` when nothing looks at it.
 */
internal class ListRef(
    val path: List<Int>,
    val rules: GenList?,
    val containers: List<SrcDecl>,
    val declarations: List<SrcDecl>,
) {
    /** [name] as the report names a declaration of this list: `Outer.Inner.name`. */
    fun qualified(name: String): String = (containers.map { it.name } + name).joinToString(".")

    /** The line [declaration] of this list was made for, or `null`. */
    fun lineOf(declaration: SrcDecl): GenLine? = declaration.origin?.let { rules?.lines?.getOrNull(it) }

    override fun toString(): String = "ListRef(${containers.map { it.name }})"
}

/** Every list of declarations of the case: the file's first, then the members of each class, interface or object. */
internal fun GenCase.lists(): List<ListRef> {
    val found = mutableListOf<ListRef>()
    fun walk(path: List<Int>, rules: GenList?, containers: List<SrcDecl>, declarations: List<SrcDecl>) {
        val list = ListRef(path, rules, containers, declarations)
        found += list
        declarations.forEachIndexed { index, declaration ->
            if (!declaration.kind.isType) return@forEachIndexed
            walk(path + index, list.lineOf(declaration)?.members, containers + declaration, declaration.members.orEmpty())
        }
    }
    walk(emptyList(), declarationRules, emptyList(), declarations)
    return found
}

/** [this] with the list at [path] replaced by what [transform] makes of it. */
internal fun GenCase.updateList(path: List<Int>, transform: (List<SrcDecl>) -> List<SrcDecl>): GenCase =
    copy(declarations = updateIn(declarations, path, transform))

private fun updateIn(declarations: List<SrcDecl>, path: List<Int>, transform: (List<SrcDecl>) -> List<SrcDecl>): List<SrcDecl> {
    if (path.isEmpty()) return transform(declarations)
    return declarations.mapIndexed { index, declaration ->
        if (index == path.first()) declaration.copy(members = updateIn(declaration.members.orEmpty(), path.drop(1), transform)) else declaration
    }
}

/** [this] with the declaration at [index] of the list at [path] replaced by [replacement]. */
internal fun GenCase.replaceDeclaration(path: List<Int>, index: Int, replacement: SrcDecl): GenCase =
    updateList(path) { list -> list.mapIndexed { i, declaration -> if (i == index) replacement else declaration } }

/** A declaration that writes nothing but its kind and its name, `Extra<n>`: no line of the cases names it. */
internal fun extraDeclaration(kind: GenKind, number: Int): SrcDecl =
    plainDeclaration(kind, nameOf(kind, "Extra") + number, emptyList(), emptyList(), container = null)

/** [this] with every list of declarations and the imports in random order. */
internal fun GenCase.shuffled(random: Random): GenCase = copy(imports = imports.shuffled(random), declarations = shuffledTree(declarations, random))

private fun shuffledTree(declarations: List<SrcDecl>, random: Random): List<SrcDecl> =
    declarations.map { declaration ->
        declaration.members?.let { declaration.copy(members = shuffledTree(it, random)) } ?: declaration
    }.shuffled(random)

/**
 * Cases smaller than [this] that still follow their rules: a rule kind left out, a line of the
 * file dropped with its declarations, a `{ }` dropped, a declaration no required line asks for
 * dropped.
 */
internal fun GenCase.shrinks(): List<GenCase> = buildList {
    if (packageLines.isNotEmpty()) add(copy(packageLines = emptyList()))
    if (importLines.isNotEmpty()) add(copy(importLines = emptyList()))
    imports.forEachIndexed { index, import ->
        // The required import and the annotations' imports keep the case following its rules.
        if (import.path != REQUIRED_IMPORT && !import.path.startsWith("com.example.annotations")) {
            add(copy(imports = imports.filterIndexed { i, _ -> i != index }))
        }
    }
    declarationRules?.let { rules ->
        rules.lines.indices.forEach { add(withoutFileLine(rules, it)) }
        rules.lines.forEachIndexed { index, line ->
            if (line.members != null) add(copy(declarationRules = rules.replacingLine(index, line.copy(members = null))))
        }
        if (rules.anythingElse.isNotEmpty()) {
            val withoutAnythingElse = GenList(rules.entries.filterNot { it is GenAnythingElse })
            add(copy(declarationRules = withoutAnythingElse, declarations = declarations.filter { it.origin != null }))
        }
    }
    for (list in lists()) {
        list.declarations.forEachIndexed { index, declaration ->
            if (list.lineOf(declaration)?.isRequired != true) add(updateList(list.path) { it.filterIndexed { i, _ -> i != index } })
        }
    }
}

private fun GenCase.withoutFileLine(rules: GenList, lineIndex: Int): GenCase {
    val line = rules.lines[lineIndex]
    val entries = rules.entries.toMutableList().apply { removeAt(rules.entries.indexOfFirst { it === line }) }
    val kept = declarations.filter { it.origin != lineIndex }.map { declaration ->
        val origin = declaration.origin
        if (origin != null && origin > lineIndex) declaration.copy(origin = origin - 1) else declaration
    }
    return copy(declarationRules = GenList(entries), declarations = kept)
}

/** [this] with its line at [lineIndex] (an index of [GenList.lines]) replaced by [line]. */
internal fun GenList.replacingLine(lineIndex: Int, line: GenLine): GenList {
    val old = lines[lineIndex]
    return GenList(entries.map { if (it === old) line else it })
}
