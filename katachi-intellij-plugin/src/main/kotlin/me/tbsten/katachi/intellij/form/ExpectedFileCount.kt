package me.tbsten.katachi.intellij.form

/**
 * One checked template, as far as the footer's file count needs it.
 *
 * [fileCount] is `TemplateSummary.fileCount` (`null` when the preview failed); [branchDeltas] holds
 * `addedFiles.size - removedFiles.size` of each `TemplateBranch` whose value the user set away from
 * the preview value.
 */
internal data class FileCountSource(
    val fileCount: Int?,
    val branchDeltas: List<Int> = emptyList(),
)

/**
 * The footer's "N files". [isApproximate] shows it as "about N"; [hasUnknown] as "N+" (a checked
 * template with no preview, whose files are not counted in [total]).
 */
internal data class ExpectedFileCount(
    val total: Int,
    val isApproximate: Boolean,
    val hasUnknown: Boolean,
)

/**
 * Sums the expected files of the checked [templates], following the footer rule of the screen spec.
 *
 * A single differing branch is exact. Two or more differing branches in one template are only added
 * up, which ignores how the branches interact, so the result is approximate. A template never goes
 * below zero files.
 *
 * TODO: not wired yet; the footer of the list + inline form screen will call it.
 */
internal fun expectedFileCountOf(templates: List<FileCountSource>): ExpectedFileCount {
    var total = 0
    var isApproximate = false
    var hasUnknown = false
    for (template in templates) {
        val base = template.fileCount
        if (base == null) {
            hasUnknown = true
            continue
        }
        total += (base + template.branchDeltas.sum()).coerceAtLeast(0)
        if (template.branchDeltas.size >= 2) isApproximate = true
    }
    return ExpectedFileCount(total = total, isApproximate = isApproximate, hasUnknown = hasUnknown)
}
