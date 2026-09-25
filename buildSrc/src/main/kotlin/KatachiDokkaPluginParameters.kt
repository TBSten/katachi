package buildsrc.convention

import groovy.json.JsonOutput
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.jetbrains.dokka.gradle.engine.plugins.DokkaPluginParametersBaseSpec
import org.jetbrains.dokka.gradle.internal.InternalDokkaGradlePluginApi
import javax.inject.Inject

/**
 * The typed Gradle side of `:tool:dokka`'s configuration (`KatachiDokkaConfiguration`).
 *
 * Dokka Gradle Plugin v2 only takes plugin settings through a subclass of
 * [DokkaPluginParametersBaseSpec] registered with `pluginsConfiguration.registerBinding`; the
 * JSON map it used to take is an error now. Every property is optional: what is left unset is
 * left out of the JSON, and the plugin's own default applies.
 *
 * ```kotlin
 * dokka {
 *     pluginsConfiguration {
 *         registerBinding(KatachiDokkaPluginParameters::class, KatachiDokkaPluginParameters::class)
 *         register<KatachiDokkaPluginParameters>("katachi") {
 *             baseUrl.set("https://example.com/api-docs/")
 *         }
 *     }
 * }
 * ```
 */
@OptIn(InternalDokkaGradlePluginApi::class)
abstract class KatachiDokkaPluginParameters @Inject constructor(
    name: String,
) : DokkaPluginParametersBaseSpec(name, "me.tbsten.katachi.dokka.KatachiDokkaPlugin") {
    @get:Input
    @get:Optional
    abstract val featuredTitle: Property<String>

    @get:Input
    @get:Optional
    abstract val llms: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val llmsFull: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val pageMarkdown: Property<Boolean>

    /**
     * The public URL of the published output root, ending in `/`: the aggregated site in a
     * multi-module build. Give every run the same value; a module's run leaves its links for the
     * aggregating run to make absolute.
     */
    @get:Input
    @get:Optional
    abstract val baseUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val projectSummary: Property<String>

    /** Left out of the JSON while empty, so an empty list means the plugin's default patterns. */
    @get:Input
    @get:Optional
    abstract val optionalPackagePatterns: ListProperty<String>

    override fun jsonEncode(): String = JsonOutput.toJson(
        buildMap {
            featuredTitle.orNull?.let { put("featuredTitle", it) }
            llms.orNull?.let { put("llms", it) }
            llmsFull.orNull?.let { put("llmsFull", it) }
            pageMarkdown.orNull?.let { put("pageMarkdown", it) }
            baseUrl.orNull?.let { put("baseUrl", it) }
            projectSummary.orNull?.let { put("projectSummary", it) }
            optionalPackagePatterns.orNull?.takeIf { it.isNotEmpty() }?.let { put("optionalPackagePatterns", it) }
        },
    )
}
