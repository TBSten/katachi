package me.tbsten.katachi.test.konsist.declarations

/*
 * The source half of a random case: for each line the declarations that follow it, for each
 * `anythingElse()` some it allows, and the imports the import lines allow.
 */

/** The declarations of [list], in random order: what each line was made for, and what only `anythingElse()` allows. */
internal fun GenContext.sourceOf(list: GenList, placement: GenPlacement, depth: Int, container: GenKind?): List<SrcDecl> {
    val instances = list.lines.withIndex().flatMap { (index, line) -> instancesOf(line, index, placement, depth, container) }
    val anythingElse = list.anythingElse.flatMap { it.alternatives }
    val free = if (anythingElse.isEmpty()) {
        emptyList()
    } else {
        List(random.nextInt(0, 3)) { freeDeclaration(random.pick(anythingElse), placement, container) }
    }
    return (instances + free).shuffled(random)
}

private fun GenContext.instancesOf(line: GenLine, index: Int, placement: GenPlacement, depth: Int, container: GenKind?): List<SrcDecl> {
    if (line.prohibited) return emptyList()
    val count = when {
        line.isRequired -> 1
        '*' !in line.name -> random.nextInt(0, 2)
        else -> random.nextInt(0, 3)
    }
    return List(count) { n ->
        val kind = random.pick(line.kinds)
        val name = when {
            '*' !in line.name -> line.name.fillIn(fileName)
            line.isBareWildcard -> nameOf(kind, if (stemsLeft > 0) stem() else "Spare${number()}")
            else -> line.name.removeSuffix("*") + listOf("One", "Two")[n]
        }
        instanceOf(line, kind, name, index, placement, depth, container)
    }
}

/** A declaration named [name] of [kind] that [line] matches in full. */
private fun GenContext.instanceOf(
    line: GenLine,
    kind: GenKind,
    name: String,
    origin: Int,
    placement: GenPlacement,
    depth: Int,
    container: GenKind?,
): SrcDecl {
    val alternative = random.pick(line.alternatives)
    val keywords = writtenModifiers(alternative.keywords, placement)
    val annotations = alternative.annotations.map { spellAnnotation(it) } + listOfNotNull("Suppress(\"unused\")".takeIf { random.chance(10) })
    val declaration = when {
        kind.isType -> SrcDecl(
            kind = kind,
            name = name,
            nameWritten = !(name == "Companion" && "companion" in keywords && random.chance(50)),
            modifiers = keywords,
            annotations = annotations,
            supertypes = line.superclasses.map { spellSupertype(it, kind) } + listOfNotNull("Closeable".takeIf { random.chance(15) }),
            members = when {
                line.members != null -> sourceOf(line.members, GenPlacement.ClassBody, depth + 1, container = kind)
                random.chance(30) -> uncheckedDeclarations(GenPlacement.ClassBody)
                else -> null
            },
        )
        kind == GenKind.Function -> functionOf(line, name, keywords, annotations, container)
        kind == GenKind.TypeAlias -> SrcDecl(kind, name, keywords, annotations)
        else -> propertyOf(line, kind, name, keywords, annotations, container)
    }
    return declaration.copy(origin = origin)
}

private fun GenContext.functionOf(line: GenLine, name: String, keywords: List<String>, annotations: List<String>, container: GenKind?): SrcDecl {
    val parameters = line.parameters?.map { spellType(typeSampleOf(it)) } ?: List(random.nextInt(0, 3)) { spellType(random.pick(TYPE_SAMPLES)) }
    val noBody = keywords.any { it == "abstract" || it == "expect" || it == "external" } || (container == GenKind.Interface && random.chance(40))
    val blockOrNone = if (noBody) GenBody.None else GenBody.Block
    val anyBody = if (noBody) GenBody.None else random.pick(listOf(GenBody.Block, GenBody.Expression))
    val returnPattern = line.returnType
    val (returnType, body) = when {
        // Unit is also what a block body returns without writing it.
        returnPattern == UNIT_SAMPLE.pattern -> spellType(UNIT_SAMPLE).takeIf { random.chance(50) } to blockOrNone
        returnPattern != null -> spellType(typeSampleOf(returnPattern)) to anyBody
        else -> when (random.nextInt(3)) {
            0 -> "Int" to anyBody
            1 -> null to blockOrNone
            else -> null to (if (noBody) GenBody.None else GenBody.Expression)
        }
    }
    return SrcDecl(GenKind.Function, name, keywords, annotations, parameters = parameters, returnType = returnType, body = body)
}

private fun GenContext.propertyOf(
    line: GenLine,
    kind: GenKind,
    name: String,
    keywords: List<String>,
    annotations: List<String>,
    container: GenKind?,
): SrcDecl {
    val asked = line.propertyType?.let { spellType(typeSampleOf(it)) }
    val written = asked ?: spellType(random.pick(TYPE_SAMPLES)).takeIf { random.chance(40) }
    val noInitializer = keywords.any { it == "lateinit" || it == "abstract" || it == "expect" } ||
        (container == GenKind.Interface && random.chance(50))
    val inConstructor = container == GenKind.Class && !noInitializer && "const" !in keywords && random.chance(50)
    // A property with neither a type nor an initializer would not be Kotlin, nor one in a constructor without a type.
    val type = if (noInitializer || inConstructor) written ?: spellType(random.pick(TYPE_SAMPLES)) else written
    return SrcDecl(
        kind = kind,
        name = name,
        modifiers = keywords,
        annotations = annotations,
        propertyType = type,
        body = if (noInitializer) GenBody.None else GenBody.Expression,
        inConstructor = inConstructor,
    )
}

/** [asked] as a declaration writes it: `public` left out at times, a visibility nobody asked for put in at times. */
private fun GenContext.writtenModifiers(asked: List<String>, placement: GenPlacement): List<String> {
    if ("public" in asked && random.chance(50)) return asked - "public"
    if (asked.any { it in VISIBILITIES } || !random.chance(25)) return asked
    val visibilities = listOf("public", "internal", "private") + if (placement == GenPlacement.ClassBody) listOf("protected") else emptyList()
    return listOf(random.pick(visibilities)) + asked
}

private fun GenContext.spellAnnotation(name: String): String {
    val (spelling, import) = random.pick(ANNOTATION_SPELLINGS.getValue(name))
    import?.let { annotationImports += it }
    return spelling
}

private fun GenContext.spellSupertype(pattern: String, kind: GenKind): String {
    val sample = supertypeSampleOf(pattern)
    return random.pick(if (kind == GenKind.Interface) sample.interfaceSpellings else sample.classSpellings).fillIn(fileName)
}

private fun GenContext.spellType(sample: TypeSample): String = random.pick(sample.matching).fillIn(fileName)

/** A declaration only an `anythingElse()` alternative allows: named `Helper<n>`, carrying what [alternative] asks for. */
private fun GenContext.freeDeclaration(alternative: GenModifiers, placement: GenPlacement, container: GenKind?): SrcDecl {
    val kind = random.pick(if (placement == GenPlacement.File) GenKind.entries else GenKind.entries - GenKind.TypeAlias)
    val keywords = if ("public" in alternative.keywords && random.chance(50)) alternative.keywords - "public" else alternative.keywords
    return plainDeclaration(kind, nameOf(kind, "Helper") + number(), keywords, alternative.annotations.map { spellAnnotation(it) }, container)
}

/** A few declarations of a list that nothing looks at. */
internal fun GenContext.uncheckedDeclarations(placement: GenPlacement): List<SrcDecl> = List(random.nextInt(1, 3)) {
    val kind = random.pick(if (placement == GenPlacement.File) GenKind.entries else GenKind.entries - GenKind.TypeAlias)
    val visibility = random.pickOrNull(listOf("internal", "private")).takeIf { random.chance(40) }
    plainDeclaration(kind, nameOf(kind, "Free") + number(), listOfNotNull(visibility), emptyList(), container = null)
}

/** A declaration of [kind] that writes nothing but [keywords] and [annotations]: no type, no parameter, no member. */
internal fun plainDeclaration(
    kind: GenKind,
    name: String,
    keywords: List<String>,
    annotations: List<String>,
    container: GenKind?,
): SrcDecl = when (kind) {
    GenKind.Function -> SrcDecl(kind, name, keywords, annotations, body = if (container == GenKind.Interface) GenBody.None else GenBody.Block)
    GenKind.Val, GenKind.Var -> SrcDecl(kind, name, keywords, annotations, body = GenBody.Expression)
    GenKind.TypeAlias -> SrcDecl(kind, name, keywords, annotations)
    else -> SrcDecl(kind, name, keywords, annotations, members = emptyList())
}

/** Whether the import lines allow [path]: some allowed line matches it, and no `not.` line does. */
internal fun importAllowed(lines: List<GenImportLine>, path: String): Boolean =
    lines.none { it.prohibited && importLineMatches(it.pattern, it.except, path) } &&
        lines.any { !it.prohibited && importLineMatches(it.pattern, it.except, path) }

/** The import lines of a case (none when [check] is `false`) and imports that follow them, the annotations' imports among them. */
internal fun GenContext.importsOf(check: Boolean, options: GenOptions): Pair<List<GenImportLine>, List<GenImport>> {
    if (!check) {
        val paths = (IMPORT_TABLE.keys.shuffled(random).take(random.nextInt(0, 4)) + annotationImports).distinct()
        return emptyList<GenImportLine>() to paths.map(::importOf)
    }
    val lines = ALLOWED_IMPORT_PATTERNS.shuffled(random).take(random.nextInt(1, 4)).map { GenImportLine(it) }.toMutableList()
    if (random.chance(40)) lines += GenImportLine(REQUIRED_IMPORT, optional = random.chance(30))
    if (options.exceptImport) {
        val (pattern, except) = PROHIBITED_IMPORTS.first { it.second.isNotEmpty() }
        lines += GenImportLine(pattern, prohibited = true, except = except)
    } else if (options.prohibitedImport || random.chance(40)) {
        val (pattern, except) = random.pick(PROHIBITED_IMPORTS)
        lines += GenImportLine(pattern, prohibited = true, except = if (random.chance(50)) except else emptyList())
    }
    if (annotationImports.any { !importAllowed(lines, it) }) lines += GenImportLine("com.example.annotations.*")
    val paths = linkedSetOf<String>()
    for (line in lines.filterNot { it.prohibited }) {
        IMPORT_TABLE.keys.filter { importLineMatches(line.pattern, line.except, it) && importAllowed(lines, it) }
            .shuffled(random).take(random.nextInt(0, 3)).forEach { paths += it }
        if (line.isRequired) paths += line.pattern
    }
    paths += annotationImports
    return lines.shuffled(random) to paths.shuffled(random).map(::importOf)
}

/** [path] as an import, now and then with an alias -- never for an annotation's import, which the source writes by its own name. */
private fun GenContext.importOf(path: String): GenImport =
    GenImport(path, alias = "Alias${number()}".takeIf { !path.endsWith("*") && path !in annotationImports && random.chance(15) })
