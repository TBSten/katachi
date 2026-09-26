package me.tbsten.katachi.dokka.navigation

import me.tbsten.katachi.dokka.KatachiDokkaConfiguration
import org.jetbrains.dokka.base.renderers.html.NavigationNode

/**
 * How the sidebar lists the packages of a module: the parsed form of
 * [KatachiDokkaConfiguration.packageNavigation].
 */
internal enum class PackageNavigation(val value: String) {
    /** Nested by `.`; a package segment without a page links to the module page. */
    HierarchicalModuleLink("hierarchical-module-link"),

    /**
     * Nested by `.`; a package segment without a page is a label that only opens and closes.
     * Rendered by [LabelledPackageNavigationPage], a copy of Dokka's rendering.
     */
    HierarchicalNoLink("hierarchical-no-link"),

    /** Dokka's own flat list of fully qualified package names. */
    Flat("flat"),
    ;

    /** [root] with its package nodes nested as this mode asks; [root] itself for [Flat]. */
    fun nest(root: NavigationNode): NavigationNode = when (this) {
        HierarchicalModuleLink -> PackageTree.nest(root) { _, label, children ->
            NavigationNode(label, root.dri, root.sourceSets, icon = null, children = children)
        }
        HierarchicalNoLink -> PackageTree.nest(root) { packageName, label, children ->
            NavigationNode(label, labelOnlyDri(packageName), root.sourceSets, icon = null, children = children)
        }
        Flat -> root
    }

    companion object {
        fun of(value: String): PackageNavigation =
            entries.firstOrNull { it.value == value } ?: throw KatachiDokkaPackageNavigationException(value)
    }
}

/** The mode [KatachiDokkaConfiguration.packageNavigation] names. */
internal val KatachiDokkaConfiguration.packageNavigationMode: PackageNavigation
    get() = PackageNavigation.of(packageNavigation)

/** [KatachiDokkaConfiguration.packageNavigation] is not one of the modes of [PackageNavigation]. */
internal class KatachiDokkaPackageNavigationException(
    val value: String,
) : IllegalArgumentException(
    "The packageNavigation setting of me.tbsten.katachi.dokka.KatachiDokkaPlugin is \"$value\", " +
        "which is not a known mode, so the sidebar cannot be built. Use one of " +
        PackageNavigation.entries.joinToString { "\"${it.value}\"" } + ", or leave it out for \"" +
        PackageNavigation.HierarchicalModuleLink.value + "\".",
)
