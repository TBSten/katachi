package me.tbsten.katachi.dokka.internal.fragment

/** The file a module's run leaves next to its output for the aggregating run to collect. */
internal const val FRAGMENT_FILE: String = "katachi-dokka-fragment.json"

/** Bumped whenever [ModuleFragment] changes shape; a fragment of another version is ignored. */
internal const val FRAGMENT_SCHEMA_VERSION: Int = 3

/**
 * What one module's run tells the aggregating run, written as JSON to [FRAGMENT_FILE].
 *
 * Only plain data: the aggregating run parses no sources, so everything it shows has to be
 * resolved here, while the module's own documentables and location provider are at hand.
 */
internal data class ModuleFragment(
    val schemaVersion: Int,
    val moduleName: String,
    val moduleSummary: String?,
    val featured: List<FragmentEntry>,
    /**
     * The llms files the module wrote, relative to its output root when written and to the
     * aggregated output root once read, in the same way as [FragmentEntry.path].
     */
    val llmsFiles: List<String>,
    /** The module's own page, relative in the same way as [FragmentEntry.path]. */
    val modulePath: String?,
    /** Whether the module wrote the Markdown version of each page next to it. */
    val pageMarkdown: Boolean,
)

/**
 * One `@featured` declaration of the module.
 *
 * [path] is relative to the module's own output root when written, and relative to the
 * aggregated output root once [ModuleFragmentStrategy] has read it.
 */
internal data class FragmentEntry(
    val name: String,
    val kind: String,
    val summary: List<SummarySegment>,
    val path: String,
)
