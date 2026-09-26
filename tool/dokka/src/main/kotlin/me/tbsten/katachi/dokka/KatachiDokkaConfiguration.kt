package me.tbsten.katachi.dokka

import org.jetbrains.dokka.plugability.ConfigurableBlock
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration

/**
 * The settings of [KatachiDokkaPlugin], read from Dokka's `pluginsConfiguration` as JSON.
 *
 * Every property has a default, so the plugin works without any configuration at all.
 *
 * ## Example
 *
 * ```json
 * "pluginsConfiguration": [
 *   {
 *     "fqPluginName": "me.tbsten.katachi.dokka.KatachiDokkaPlugin",
 *     "serializationFormat": "JSON",
 *     "values": "{ \"featuredTitle\": \"Start here\" }"
 *   }
 * ]
 * ```
 *
 * @property featuredTitle The name of the sidebar node and the heading of the llms files' section
 *   that lists the `@featured` declarations. HTML shows it with a star in front (see
 *   `me.tbsten.katachi.dokka.featured.htmlFeaturedTitle`); the llms files and the per-page
 *   Markdown keep it plain.
 * @property llms Whether each module gets an [LLMS_FILE], and the aggregated output an index of
 *   them.
 * @property llmsFull Whether each module gets an [LLMS_FULL_FILE] with every declaration's
 *   signature and KDoc.
 * @property pageMarkdown Whether every HTML page gets a Markdown version at its URL with `.md`
 *   appended, which the llms files link to.
 * @property baseUrl The public URL where the final output root is published: the aggregated
 *   output of a multi-module build, or the output of a single-module run. Links in the llms files
 *   are made absolute with it; without it they stay relative to the file, which is also correct
 *   as long as the files are read where they were generated. A module's run of a multi-module
 *   build (one that delays template substitution) ignores it, because it does not know where the
 *   aggregating run puts it: pass the same value to every run, and the aggregating run makes the
 *   modules' links absolute.
 * @property projectSummary The one-line summary under the title of the aggregated [LLMS_FILE].
 * @property optionalPackagePatterns Regular expressions matched against whole package names. A
 *   matching package, with its declarations, is listed under the `Optional` section of
 *   [LLMS_FILE], which the llms.txt format marks as skippable.
 */
public data class KatachiDokkaConfiguration(
    val featuredTitle: String = "Featured",
    val llms: Boolean = true,
    val llmsFull: Boolean = true,
    val pageMarkdown: Boolean = true,
    val baseUrl: String? = null,
    val projectSummary: String? = null,
    val optionalPackagePatterns: List<String> = listOf(".*\\.internal(\\..*)?", ".*\\.impl(\\..*)?"),
) : ConfigurableBlock

/** The llms.txt index of a module, or of the aggregated output: see https://llmstxt.org/. */
internal const val LLMS_FILE: String = "llms.txt"

/** The whole API reference of a module in one Markdown file, for tools that read everything. */
internal const val LLMS_FULL_FILE: String = "llms-full.txt"

/** The llms files this configuration asks each module for, in the order they are listed. */
internal val KatachiDokkaConfiguration.llmsFiles: List<String>
    get() = listOfNotNull(LLMS_FILE.takeIf { llms }, LLMS_FULL_FILE.takeIf { llmsFull })

/** Reads the configuration of this run, falling back to the defaults when none was given. */
internal fun DokkaContext.katachiConfiguration(): KatachiDokkaConfiguration =
    try {
        configuration<KatachiDokkaPlugin, KatachiDokkaConfiguration>(this)
    } catch (e: Exception) {
        throw KatachiDokkaConfigurationException(e)
    } ?: KatachiDokkaConfiguration()

/** The JSON given to [KatachiDokkaPlugin] through `pluginsConfiguration` could not be read. */
internal class KatachiDokkaConfigurationException(
    cause: Throwable,
) : IllegalArgumentException(
    "The pluginsConfiguration entry for me.tbsten.katachi.dokka.KatachiDokkaPlugin is not valid " +
        "JSON for KatachiDokkaConfiguration, so the documentation cannot be generated with the " +
        "settings it was meant to have. Fix the values passed to the plugin; every property " +
        "is optional, so an empty object is also accepted. Cause: ${cause.message}",
    cause,
)
