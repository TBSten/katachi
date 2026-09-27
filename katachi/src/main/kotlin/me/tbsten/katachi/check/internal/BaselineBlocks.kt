package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.internal.displayPath

/**
 * The report block of a baseline entry that holds back more than the project still has.
 *
 * Split out of `ViolationReport.kt` on size alone, as `WarningBlocks.kt` is. The path is shown
 * with [displayPath] rather than as an existing file: when every violation of the entry is gone,
 * so usually is the file.
 *
 * `How to fix:` names prune rather than update, so that whoever fixed something does not record
 * a new violation in the same breath.
 */
internal fun staleBaselineEntryBlock(violation: StaleBaselineEntry, root: String?): List<String> = buildList {
    add("[${violation.label}] ${displayPath(root, violation.path)}")
    add("${STEP}Baseline: ${displayPath(root, violation.baselinePath)} (${violation.check})")
    add("${STEP}Rule: ${violation.rule}, allowed ${violation.allowed}, found ${violation.found}")
    // The rest of the key, when there is more to it than the path: two entries on one file would
    // otherwise read the same.
    val rest = listOfNotNull(
        violation.role?.let { "role $it" },
        violation.constraint?.let { "constraint \"$it\"" },
        violation.declaration?.let { "declaration $it" },
    )
    if (rest.isNotEmpty()) add("${STEP}Entry: ${rest.joinToString(", ")}")
    add("")
    add("${STEP}How to fix:")
    val gone = violation.allowed - violation.found
    add(
        when {
            violation.found == 0 ->
                "$STEP$STEP- Every violation this entry held back is gone. Remove the entry by re-running the architecture test with"
            gone == 1 ->
                "$STEP$STEP- 1 violation this entry held back is gone. Shrink the entry by re-running the architecture test with"
            else ->
                "$STEP$STEP- $gone violations this entry held back are gone. Shrink the entry by re-running the architecture test with"
        },
    )
    add("$STEP$STEP$STEP$STEP-D$BASELINE_PRUNE_PROPERTY=true")
}
