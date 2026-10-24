package me.tbsten.katachi.test.konsist.declarations

/*
 * Changes to one declaration that keep its name but make the line it was made for no longer match
 * it in full -- one piece at a time, each with the `Differs in:` the report should give for it.
 */

/** [changed] in place of the declaration, and what the report should say differs. */
internal class AspectChange(val changed: SrcDecl, val differs: String) {
    override fun toString(): String = "AspectChange($differs)"
}

/** Every change of one piece of [declaration], made for [line], that [line] notices. */
internal fun aspectChangesOf(line: GenLine, declaration: SrcDecl, fileName: String, placement: GenPlacement): List<AspectChange> =
    modifierChanges(line, declaration, placement) +
        annotationChanges(line, declaration) +
        supertypeChanges(line, declaration, fileName) +
        propertyTypeChanges(line, declaration, fileName) +
        parameterChanges(line, declaration, fileName) +
        returnTypeChanges(line, declaration, fileName) +
        kindChanges(line, declaration)

private fun modifierChanges(line: GenLine, declaration: SrcDecl, placement: GenPlacement): List<AspectChange> = buildList {
    val alternatives = line.alternatives
    if (alternatives.all { alternative -> alternative.keywords.any { it in VISIBILITIES } }) {
        // Every alternative asks for a visibility: write one none of them asks for. Each alternative
        // then lacks one modifier, and the report names the first alternative's.
        val asked = alternatives.flatMap { alternative -> alternative.keywords.filter { it in VISIBILITIES } }.toSet()
        val others = (if (placement == GenPlacement.ClassBody) listOf("internal", "protected", "private") else listOf("internal", "private")) - asked
        val replacements = others.map { listOf(it) } + if ("public" in asked) emptyList() else listOf(listOf("public"), emptyList())
        val first = alternatives.first().keywords.first { it in VISIBILITIES }
        for (visibility in replacements) {
            val modifiers = visibility + declaration.modifiers.filterNot { it in VISIBILITIES }
            add(AspectChange(declaration.copy(modifiers = modifiers), "modifiers (missing: $first)"))
        }
    }
    val specific = alternatives.first().keywords.filterNot { it in VISIBILITIES }
    for (keyword in specific) {
        if (alternatives.all { keyword in it.keywords }) {
            add(AspectChange(declaration.copy(modifiers = declaration.modifiers - keyword), "modifiers (missing: $keyword)"))
        }
    }
}

private fun annotationChanges(line: GenLine, declaration: SrcDecl): List<AspectChange> {
    // The generator writes the annotations a line asks for first, in the order asked.
    val asked = line.alternatives.first().annotations.firstOrNull() ?: return emptyList()
    return listOf(AspectChange(declaration.copy(annotations = declaration.annotations.drop(1)), "annotations (missing: @$asked)"))
}

private fun supertypeChanges(line: GenLine, declaration: SrcDecl, fileName: String): List<AspectChange> =
    line.superclasses.mapIndexed { index, pattern ->
        val other = supertypeSampleOf(pattern).other
        val supertypes = declaration.supertypes.mapIndexed { i, written -> if (i == index) other else written }
        AspectChange(declaration.copy(supertypes = supertypes), "superclass (missing: ${pattern.fillIn(fileName)})")
    }

private fun propertyTypeChanges(line: GenLine, declaration: SrcDecl, fileName: String): List<AspectChange> {
    val pattern = line.propertyType ?: return emptyList()
    val expected = pattern.fillIn(fileName)
    val other = typeSampleOf(pattern).other
    return listOfNotNull(
        AspectChange(declaration.copy(propertyType = other), "type (is $other, expected $expected)"),
        // Inferred: only where an initializer is written, and not in a constructor, which needs the type.
        AspectChange(declaration.copy(propertyType = null), "type (not written, expected $expected)")
            .takeIf { declaration.body == GenBody.Expression && !declaration.inConstructor },
    )
}

private fun parameterChanges(line: GenLine, declaration: SrcDecl, fileName: String): List<AspectChange> {
    val patterns = line.parameters ?: return emptyList()
    val expected = patterns.withIndex().joinToString(", ", "(", ")") { (index, type) -> parameterPatternOf(index, type.fillIn(fileName)) }
    fun change(parameters: List<String>, renamed: Int? = null) = AspectChange(
        declaration.copy(parameters = parameters, renamedParameter = renamed),
        "parameters (are ${renderedParametersOf(parameters, renamed)}, expected $expected)",
    )
    // Only a parameter whose name pattern is not `*` (see parameterPatternOf) can be renamed out of its line.
    val renamable = patterns.indices.firstOrNull { it % 3 != 0 }
    return patterns.indices.map { index ->
        change(declaration.parameters.mapIndexed { i, written -> if (i == index) typeSampleOf(patterns[index]).other else written })
    } + change(declaration.parameters + "Int") + listOfNotNull(
        change(declaration.parameters.dropLast(1)).takeIf { patterns.isNotEmpty() },
        renamable?.let { change(declaration.parameters, renamed = it) },
    )
}

private fun returnTypeChanges(line: GenLine, declaration: SrcDecl, fileName: String): List<AspectChange> {
    val pattern = line.returnType ?: return emptyList()
    val expected = pattern.fillIn(fileName)
    val other = typeSampleOf(pattern).other
    val body = if (declaration.body == GenBody.None) GenBody.None else GenBody.Block
    return listOfNotNull(
        AspectChange(declaration.copy(returnType = other, body = body), "return type (is $other, expected $expected)"),
        AspectChange(declaration.copy(returnType = null, body = GenBody.Expression), "return type (not written, expected $expected)")
            .takeIf { declaration.body != GenBody.None },
    )
}

private fun kindChanges(line: GenLine, declaration: SrcDecl): List<AspectChange> {
    // Only a declaration that writes nothing but a visibility reads the same as another kind.
    if (!declaration.modifiers.all { it in VISIBILITIES }) return emptyList()
    val expected = line.kinds.joinToString(" or ") { it.keyword }
    val others = when {
        declaration.kind.isType && declaration.members.orEmpty().none { it.inConstructor } ->
            listOf(GenKind.Class, GenKind.Interface, GenKind.Object) - line.kinds.toSet()
        declaration.kind == GenKind.Val || declaration.kind == GenKind.Var -> listOf(GenKind.Val, GenKind.Var) - line.kinds.toSet()
        else -> emptyList()
    }
    return others.map { kind ->
        // An interface calls no constructor of its supertypes.
        val supertypes = if (kind == GenKind.Interface) declaration.supertypes.map { it.removeSuffix("()") } else declaration.supertypes
        AspectChange(declaration.copy(kind = kind, supertypes = supertypes), "kind (is ${kind.keyword}, expected $expected)")
    }
}
