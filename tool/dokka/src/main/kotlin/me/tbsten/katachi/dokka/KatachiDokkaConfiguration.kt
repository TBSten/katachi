package me.tbsten.katachi.dokka

import me.tbsten.katachi.dokka.llms.LLMS_FILE
import me.tbsten.katachi.dokka.llms.LLMS_FULL_FILE
import me.tbsten.katachi.dokka.navigation.PackageNavigation
import org.jetbrains.dokka.plugability.ConfigurableBlock
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration

/**
 * The settings of [KatachiDokkaPlugin], read from Dokka's `pluginsConfiguration` as JSON.
 *
 * Every property has a default, so the plugin works without any configuration at all.
 *
 * Public because it is the type argument of `pluginsConfiguration`'s
 * `configuration<KatachiDokkaPlugin, KatachiDokkaConfiguration>()` read, deserialized by Dokka
 * from a different module.
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
 *   `me.tbsten.katachi.dokka.internal.featured.htmlFeaturedTitle`); the llms files and the per-page
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
 * @property packageNavigation How the sidebar lists the packages of each module. With
 *   `"hierarchical-module-link"` (the default) they are nested by the `.`-separated segments of
 *   their names, and a segment that is not a package of its own (`com.example`) links to the
 *   module page: clicking its name opens the module page, where the sidebar highlights the module,
 *   and leaves that segment closed (its arrow opens it). `"hierarchical-no-link"` nests them the same way, but such a segment is a label
 *   that only opens and closes; it needs a copy of Dokka's sidebar rendering, which may have to
 *   follow a Dokka upgrade by hand. `"flat"` keeps Dokka's list of full package names. In both
 *   nested modes a segment without a package of its own is merged with its only sub-package
 *   (`com > example` becomes `com.example`). The Featured node stays first in every mode.
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
    val packageNavigation: String = "hierarchical-module-link",
) : ConfigurableBlock

/**
 * Reads the configuration of this run, falling back to the defaults when none was given.
 *
 * Values that are checked beyond their JSON type, such as
 * [KatachiDokkaConfiguration.packageNavigation], are checked here, so that every run rejects them —
 * also the aggregating run of a multi-module build, which never builds a module's sidebar.
 */
internal fun DokkaContext.katachiConfiguration(): KatachiDokkaConfiguration {
    val read = try {
        configuration<KatachiDokkaPlugin, KatachiDokkaConfiguration>(this)
    } catch (e: Exception) {
        throw KatachiDokkaConfigurationException(e)
    } ?: KatachiDokkaConfiguration()
    PackageNavigation.of(read.packageNavigation)
    return read
}

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
