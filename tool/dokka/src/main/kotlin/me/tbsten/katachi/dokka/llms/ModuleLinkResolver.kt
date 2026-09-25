package me.tbsten.katachi.dokka.llms

import org.jetbrains.dokka.DokkaConfiguration.DokkaModuleDescription
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.resolvers.external.ExternalLocationProvider
import org.jetbrains.dokka.base.resolvers.shared.ExternalDocumentation
import org.jetbrains.dokka.base.resolvers.shared.PackageList
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.query
import java.io.File
import java.net.URL

/**
 * Finds the page of a DRI among the modules the aggregating run puts together.
 *
 * The same resolution as the all-modules-page plugin's `DefaultExternalModuleLinkResolver`,
 * which fills in the HTML's `ResolveLinkCommand`s: each module's `package-list` is read as the
 * external documentation of the module's directory, and Dokka's own
 * `externalLocationProviderFactory` turns a DRI into a path there. It is rebuilt here from
 * `DokkaBase` so that this plugin never refers to the all-modules-page plugin, which the modules'
 * runs do not load.
 */
internal class ModuleLinkResolver(private val context: DokkaContext) {
    private class ModuleProvider(val module: DokkaModuleDescription, val provider: ExternalLocationProvider)

    private val providers: List<ModuleProvider> by lazy {
        val factories = context.plugin<DokkaBase>().query { externalLocationProviderFactory }
        context.configuration.modules.flatMap { module ->
            val documentation = packageListOf(module)?.let { ExternalDocumentation(URL("$SCHEME${relativePathOf(module)}"), it) }
                ?: return@flatMap emptyList()
            factories.mapNotNull { factory -> factory.getExternalLocationProvider(documentation)?.let { ModuleProvider(module, it) } }
        }
    }

    /**
     * The path of [dri]'s page, or of its [page] version, relative to the aggregated output root, or
     * null when no module has that page.
     *
     * A package list answers for every DRI in its packages, with a path built from the name, so
     * the page is also looked up in the module's output. Dokka does that only when two modules
     * share a package; doing it always keeps a link to a declaration without a page, such as a
     * private one, from pointing at nothing.
     */
    fun resolve(dri: DRI, page: LinkedPage = LinkedPage.Html): String? = providers.firstNotNullOfOrNull { candidate ->
        val path = candidate.provider.resolve(dri)?.takeIf { it.startsWith(SCHEME) }?.removePrefix(SCHEME)
            ?: return@firstNotNullOfOrNull null
        val inModule = path.substringBefore('#').removePrefix(relativePathOf(candidate.module)).removePrefix("/")
        val output = candidate.module.sourceOutputDirectory
        when {
            !File(output, inModule).isFile -> null
            // A module generated without the Markdown pages still has its HTML page to link to.
            File(output, page.pathOf(inModule)).isFile -> page.pathOf(path)
            else -> path
        }
    }

    private fun packageListOf(module: DokkaModuleDescription): PackageList? = module.sourceOutputDirectory
        .walkTopDown()
        .maxDepth(PACKAGE_LIST_DEPTH)
        .firstOrNull { it.name == PackageList.PACKAGE_LIST_NAME }
        ?.let { PackageList.load(it.toURI().toURL(), JDK_VERSION, offlineMode = true) }

    private fun relativePathOf(module: DokkaModuleDescription): String = module.directoryIn(context.configuration.outputDir)

    private companion object {
        // A URL whose path is the module's directory, so that what the provider answers is that
        // directory followed by the page's path — the same trick as Dokka's resolver.
        const val SCHEME = "file:/"
        const val PACKAGE_LIST_DEPTH = 3
        const val JDK_VERSION = 8
    }
}

/** The module's directory in the aggregated [outputDir], relative to it, without slashes around it. */
internal fun DokkaModuleDescription.directoryIn(outputDir: File): String {
    val path = relativePathToOutputDirectory
    val relative = if (path.isAbsolute) path.relativeToOrSelf(outputDir) else path
    return relative.invariantSeparatorsPath.trim('/')
}
