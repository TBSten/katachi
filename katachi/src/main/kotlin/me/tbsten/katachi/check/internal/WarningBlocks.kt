package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.AmbiguousLayout
import me.tbsten.katachi.dsl.internal.DeclaredByGradle
import me.tbsten.katachi.internal.displayExistingPath
import me.tbsten.katachi.internal.displayPath

internal fun ambiguousLayoutBlock(violation: AmbiguousLayout, root: String?): List<String> = buildList {
    val roles = violation.claims.size
    val onFiles = violation.overlappingFiles.isNotEmpty()
    // On files the path is one of them, found on disk; otherwise it is the declared path, which
    // may be a pattern.
    val path = if (onFiles) displayExistingPath(root, violation.path) else displayPath(root, violation.path)
    add("[${violation.label}] $path")
    if (onFiles) {
        add("$STEP$roles roles claim this file, so it belongs to all of them.")
        add("${STEP}Every constraint those roles declare is checked against it.")
        // The first entry is `path`, already on the line above.
        val others = violation.overlappingFiles.drop(1)
        if (others.isNotEmpty()) add(listedLine("Other file", "Other files", others.map { displayExistingPath(root, it) }))
    } else {
        add("$STEP$roles roles declare this path, so a file here belongs to all of them.")
    }
    add("")
    add("${STEP}Declared by:")
    val width = violation.claims.maxOf { it.role.qualifiedName.length }
    for (claim in violation.claims) {
        add("$STEP$STEP${claim.role.qualifiedName.padEnd(width)} ${claim.declaredAt}")
    }
    add("")
    add("${STEP}How to fix:")
    addAll(ambiguousLayoutFixLines(onFiles = onFiles, plural = roles > 2))
}

private fun ambiguousLayoutFixLines(onFiles: Boolean, plural: Boolean): List<String> = when {
    onFiles && plural -> listOf(
        "$STEP$STEP- Narrow the declarations so that each of these files is claimed by one role",
        "$STEP$STEP- Or keep the overlap, and make every constraint of all of them hold for these files",
    )
    onFiles -> listOf(
        "$STEP$STEP- Narrow one of the two declarations so that each of these files is claimed by one role",
        "$STEP$STEP- Or keep the overlap, and make every constraint of both roles hold for these files",
    )
    plural -> listOf(
        "$STEP$STEP- Keep the path in the role its files belong to, and remove it from the others",
        "$STEP$STEP- Narrow the ones that are about different files",
    )
    else -> listOf(
        "$STEP$STEP- Keep the path in the role its files belong to, and remove it from the other",
        "$STEP$STEP- Narrow one of them if they are about different files",
    )
}

internal fun ignoredLayoutBlock(violation: IgnoredLayout, root: String?): List<String> = buildList {
    add("[${violation.label}] ${displayPath(root, violation.path)}")
    if (violation.partly) {
        add(
            "${STEP}Role ${violation.role.qualifiedName} declares this path, but a wildcard ignore() can match a " +
                "directory under it, so files in a directory of that name are not checked.",
        )
    } else {
        add(
            "${STEP}Role ${violation.role.qualifiedName} declares this path, but every file it allows is in a " +
                "directory that is ignored, so none of them is checked.",
        )
    }
    add("${STEP}Declared at: ${violation.declaredAt}")
    add("")
    add("${STEP}Hidden by:")
    for (ignore in violation.hiddenBy) add("$STEP$STEP${hiddenByLine(ignore, root)}")
    add("")
    add("${STEP}How to fix:")
    addAll(ignoredLayoutFixLines(violation))
}

private fun hiddenByLine(ignore: HidingIgnore, root: String?): String {
    val path = displayPath(root, ignore.path)
    val module = ignore.gradleModuleName ?: return "$path (ignore() of ${ignore.role.qualifiedName} at ${ignore.declaredAt})"
    return "$path (the build directory of $module, which gradle() at ${ignore.declaredAt} ignores)"
}

private fun ignoredLayoutFixLines(violation: IgnoredLayout): List<String> {
    if (violation.partly) {
        return listOf(
            "$STEP$STEP- If files in such a directory are to be checked, narrow the ignore() so that it leaves this path out, " +
                "for example by naming the directory instead of using a wildcard",
            "$STEP$STEP- Otherwise this is intended: a directory with that name under this path is never checked",
        )
    }
    val written = when (violation.hiddenBy.count { it.gradleModule == null }) {
        0 -> null
        1 -> "remove the ignore(), or narrow it so that it leaves this path out"
        else -> "remove those ignore() calls, or narrow them so that they leave this path out"
    }
    val byGradle = "declare the Gradle files without gradle(), which always ignores the build directory of every module"
        .takeIf { violation.hiddenBy.any { it.gradleModule != null } }
    val unhide = listOfNotNull(written, byGradle).joinToString(", and ")
    // A line gradle() wrote is not the definition's to remove, only to stop hiding.
    if (violation.role[DeclaredByGradle] == true) {
        return listOf("$STEP$STEP- ${unhide.replaceFirstChar { it.uppercaseChar() }}: gradle() declares this path itself")
    }
    return listOf(
        "$STEP$STEP- If these files are to be checked, $unhide",
        "$STEP$STEP- Otherwise remove this path from ${violation.role.qualifiedName}: no file it allows is ever checked",
    )
}

internal fun missingDescriptionBlock(violation: MissingDescription, root: String?): List<String> = listOf(
    "[${violation.label}] ${displayPath(root, violation.path)}",
    "${STEP}Role ${violation.role.qualifiedName} may live in ${violation.otherPlaces.size + 1} places, " +
        "and this one does not say when to use it.",
    listedLine("Other place", "Other places", violation.otherPlaces.map { displayPath(root, it) }),
    "${STEP}Declared at: ${violation.declaredAt}",
    "",
    "${STEP}How to fix:",
    "$STEP$STEP- Write `description = \"...\"` in this block, saying which files belong here " +
        "rather than in the others",
    "$STEP$STEP- Or merge this place into another if the two are really the same place",
)

private const val LISTED_LIMIT: Int = 3

private fun listedLine(singular: String, plural: String, items: List<String>): String {
    val label = if (items.size == 1) singular else plural
    val shown = items.take(LISTED_LIMIT).joinToString(", ")
    val hidden = items.size - LISTED_LIMIT
    return if (hidden > 0) "$STEP$label: $shown, and $hidden more" else "$STEP$label: $shown"
}
