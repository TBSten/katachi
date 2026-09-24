package me.tbsten.katachi.check

import me.tbsten.katachi.scan.AmbiguousLayout
import me.tbsten.katachi.scan.MissingDescription

/**
 * The report blocks of the Warning violations — the ones a `layout { }` produces without a
 * failing file, whether they were read off the declarations or found by the walk.
 *
 * Split out of `ViolationReport.kt` on size alone — see `ConstraintBlocks.kt` for the same
 * reasoning. The shared pieces (`STEP`) stay defined once, in `ViolationReport.kt`.
 */

/**
 * Two or more roles claiming the same thing.
 *
 * The block has two wordings, chosen by [AmbiguousLayout.overlappingFiles], because the two
 * ways the violation is raised give the reader different things to act on. From the
 * declarations, the path is a pattern and the statement is about a file that may not exist
 * yet; from the walk, the path is a file that does exist, there are usually more of them
 * behind it, and the consequence the reader missed is that both roles' constraints are being
 * checked against all of them.
 *
 * `How to fix:` is written, unlike [me.tbsten.katachi.scan.ViolationKind.Constraint]'s blocks:
 * the reason those stay silent is that a constraint's body is arbitrary Kotlin katachi does not
 * read, and neither reason applies here — katachi already knows everything an
 * [AmbiguousLayout] means, because it derived the violation itself.
 */
internal fun ambiguousLayoutBlock(violation: AmbiguousLayout): List<String> = buildList {
    val roles = violation.claims.size
    val onFiles = violation.overlappingFiles.isNotEmpty()
    add("[${violation.label}] ${violation.path}")
    if (onFiles) {
        add("$STEP$roles roles claim this file, so it belongs to all of them.")
        add("${STEP}Every constraint those roles declare is checked against it.")
        // The first entry is `path`, already on the line above.
        val others = violation.overlappingFiles.drop(1)
        if (others.isNotEmpty()) add(listedLine("Other file", "Other files", others))
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

/**
 * The two `How to fix:` lines, for each wording and for exactly two claimants or more than two.
 *
 * [AmbiguousLayout.claims] is "always two or more" ([AmbiguousLayout]'s own KDoc), so "the
 * other" / "one of them" would misread the moment a third role joins a path — there would be
 * two others left, not one. Plural wording covers that case; singular reads better for the
 * common one.
 *
 * Neither file-level line offers to delete a declaration. The files are there and some role
 * has to keep them, so the choice is which pattern stops matching them — or, just as validly,
 * none of them: an overlap the author meant is allowed, and the second line says what living
 * with it costs rather than pretending it is forbidden.
 */
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

/**
 * A role with more than one place, one of which does not say when it is the right one.
 *
 * `How to fix:` is written for the same reason [ambiguousLayoutBlock] writes one, and neither
 * line offers to delete the block. A place always holds files of the role's own — that is what
 * makes it a place at all — so "remove this declaration", which reads well under
 * `[MissingFile]` because nothing is there, would here be katachi recommending a change it
 * already knows turns every file below into an `[UnexpectedFile]`.
 */
internal fun missingDescriptionBlock(violation: MissingDescription): List<String> = listOf(
    "[${violation.label}] ${violation.path}",
    "${STEP}Role ${violation.role.qualifiedName} may live in ${violation.otherPlaces.size + 1} places, " +
        "and this one does not say when to use it.",
    listedLine("Other place", "Other places", violation.otherPlaces),
    "${STEP}Declared at: ${violation.declaredAt}",
    "",
    "${STEP}How to fix:",
    "$STEP$STEP- Write `description = \"...\"` in this block, saying which files belong here " +
        "rather than in the others",
    "$STEP$STEP- Or merge this place into another if the two are really the same place",
)

/**
 * How many items a Warning block names before it starts counting them instead.
 *
 * The same three `LayoutIndex` offers nearby locations with, and for the same reason: such a
 * line exists to show what one thing sits among, and a role living in a dozen modules — or an
 * overlap landing on forty files — is answered by the count rather than by the list.
 */
private const val LISTED_LIMIT: Int = 3

/** `Other places: a, b, c, and 5 more`, with [singular] used when [items] holds exactly one. */
private fun listedLine(singular: String, plural: String, items: List<String>): String {
    val label = if (items.size == 1) singular else plural
    val shown = items.take(LISTED_LIMIT).joinToString(", ")
    val hidden = items.size - LISTED_LIMIT
    return if (hidden > 0) "$STEP$label: $shown, and $hidden more" else "$STEP$label: $shown"
}
