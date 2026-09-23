package me.tbsten.katachi.check

import me.tbsten.katachi.scan.AmbiguousLayout
import me.tbsten.katachi.scan.MissingDescription

/**
 * The report block of the declaration-only Warning violations.
 *
 * Split out of `ViolationReport.kt` on size alone — see `ConstraintBlocks.kt` for the same
 * reasoning. The shared pieces (`STEP`) stay defined once, in `ViolationReport.kt`.
 */

/**
 * Two or more roles claiming the same path outright.
 *
 * `How to fix:` is written, unlike [me.tbsten.katachi.scan.ViolationKind.Constraint]'s blocks:
 * the reason those stay silent is that a constraint's body is arbitrary Kotlin katachi does not
 * read, and neither reason applies here — katachi already knows everything an
 * [AmbiguousLayout] means, because it derived the violation from the declarations itself.
 */
internal fun ambiguousLayoutBlock(violation: AmbiguousLayout): List<String> = buildList {
    add("[${violation.label}] ${violation.path}")
    add("$STEP${violation.claims.size} roles declare this path, so a file here belongs to all of them.")
    add("")
    add("${STEP}Declared by:")
    val width = violation.claims.maxOf { it.role.qualifiedName.length }
    for (claim in violation.claims) {
        add("$STEP$STEP${claim.role.qualifiedName.padEnd(width)} ${claim.declaredAt}")
    }
    add("")
    add("${STEP}How to fix:")
    addAll(ambiguousLayoutFixLines(plural = violation.claims.size > 2))
}

/**
 * The two `How to fix:` lines, worded for exactly two claimants or for more than two.
 *
 * [AmbiguousLayout.claims] is "always two or more" ([AmbiguousLayout]'s own KDoc), so "the
 * other" / "one of them" would misread the moment a third role joins a path — there would be
 * two others left, not one. Plural wording covers that case; singular reads better for the
 * common one.
 */
private fun ambiguousLayoutFixLines(plural: Boolean): List<String> = if (plural) {
    listOf(
        "$STEP$STEP- Keep the path in the role its files belong to, and remove it from the others",
        "$STEP$STEP- Narrow the ones that are about different files",
    )
} else {
    listOf(
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
    otherPlacesLine(violation.otherPlaces),
    "${STEP}Declared at: ${violation.declaredAt}",
    "",
    "${STEP}How to fix:",
    "$STEP$STEP- Write `description = \"...\"` in this block, saying which files belong here " +
        "rather than in the others",
    "$STEP$STEP- Or merge this place into another if the two are really the same place",
)

/**
 * How many of the role's other places the block names before it starts counting them instead.
 *
 * The same three `LayoutIndex` offers nearby locations with, and for the same reason: the line
 * exists to show what this place has to be told apart from, and a role living in a dozen modules
 * is answered by the count rather than by the list.
 */
private const val OTHER_PLACES_LIMIT: Int = 3

private fun otherPlacesLine(otherPlaces: List<String>): String {
    val label = if (otherPlaces.size == 1) "Other place" else "Other places"
    val shown = otherPlaces.take(OTHER_PLACES_LIMIT).joinToString(", ")
    val hidden = otherPlaces.size - OTHER_PLACES_LIMIT
    return if (hidden > 0) "$STEP$label: $shown, and $hidden more" else "$STEP$label: $shown"
}
