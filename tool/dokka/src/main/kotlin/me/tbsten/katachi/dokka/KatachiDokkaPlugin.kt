package me.tbsten.katachi.dokka

import me.tbsten.katachi.dokka.featured.FeaturedNavigationInstaller
import me.tbsten.katachi.dokka.featured.FeaturedTagTransformer
import me.tbsten.katachi.dokka.featured.ModulePageFeaturedTransformer
import me.tbsten.katachi.dokka.fragment.AllModulesPageFeaturedInstaller
import me.tbsten.katachi.dokka.fragment.ModuleFragmentInstaller
import me.tbsten.katachi.dokka.fragment.ModuleFragmentRegistry
import me.tbsten.katachi.dokka.fragment.ModuleFragmentStrategy
import me.tbsten.katachi.dokka.llms.LlmsIndexInstaller
import me.tbsten.katachi.dokka.llms.LlmsModuleFileStrategy
import me.tbsten.katachi.dokka.llms.LlmsModuleInstaller
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement
import org.jetbrains.dokka.templates.TemplatingPlugin

/**
 * A Dokka plugin that points readers, and AI agents, at the declarations to start from.
 *
 * - **Featured**: the declarations tagged `@featured` are listed first under each module in the
 *   sidebar, in a "Featured" section of the module page above "Packages", and — in a multi-module
 *   build — in one "Featured" section of the top page above "All modules:".
 * - **llms files**: every directory of the output — the module, each package, each type — gets
 *   an `llms.txt` (an index in the https://llmstxt.org/ format) and an `llms-full.txt` (the
 *   signatures and KDoc as Markdown). The aggregating run of a multi-module build adds an
 *   `llms.txt` at the root that links to the modules' ones.
 * - **Markdown pages**: every HTML page gets a Markdown version at its URL with `.md` appended,
 *   which is what the llms files link to.
 *
 * Every link comes with a short summary: the text after an `@llm` tag, else the first sentence or
 * two of the KDoc. Neither `@featured` nor `@llm` is shown on the HTML pages.
 *
 * Each module's run of a multi-module build leaves its links to other modules unfinished, and a
 * small JSON fragment next to its output; the aggregating run collects the fragments and resolves
 * the links against the modules' package lists, as Dokka does for the HTML. See
 * [KatachiDokkaConfiguration] to turn the files off or to make their links absolute.
 *
 * Only Dokka's own extension points are used: the tag becomes an `ExtraProperty` on the
 * `Documentable`, the sidebar entries are `NavigationNode`s of Dokka's own `NavigationPage`, the
 * sections are content built with Dokka's `PageContentBuilder`, the files are pages of the page
 * tree, and every link is resolved by Dokka's location provider. The all-modules-page plugin is
 * never referenced, so the same jar can be put on both runs' plugin classpath.
 *
 * ## Example
 *
 * Put the plugin on the Dokka plugin classpath of every documented module and of the
 * aggregating project, then add a `@featured` line to the KDoc of the declarations to list.
 * The text after `@featured`, if any, is the summary of the Featured lists; the text after
 * `@llm`, if any, is the summary everywhere else.
 *
 * ```kotlin
 * // build.gradle.kts of a documented module, and of the root project that aggregates them
 * dependencies {
 *     dokkaHtmlPlugin(project(":tool:dokka"))
 * }
 * ```
 */
public class KatachiDokkaPlugin : DokkaPlugin() {
    private val dokkaBase by lazy { plugin<DokkaBase>() }
    private val templating by lazy { plugin<TemplatingPlugin>() }

    /** Where the aggregating run keeps the fragments it read, shared by the extensions that use them. */
    internal val moduleFragmentRegistry by extensionPoint<ModuleFragmentRegistry>()

    internal val defaultModuleFragmentRegistry by extending {
        moduleFragmentRegistry providing { ModuleFragmentRegistry() }
    }

    internal val featuredTagTransformer by extending {
        CoreExtensions.documentableTransformer providing ::FeaturedTagTransformer
    }

    internal val featuredNavigationInstaller by extending {
        dokkaBase.htmlPreprocessors providing ::FeaturedNavigationInstaller order {
            after(dokkaBase.navigationPageInstaller)
        }
    }

    internal val modulePageFeaturedTransformer by extending {
        CoreExtensions.pageTransformer providing ::ModulePageFeaturedTransformer
    }

    internal val allModulesPageFeaturedInstaller by extending {
        dokkaBase.htmlPreprocessors providing ::AllModulesPageFeaturedInstaller order {
            after(dokkaBase.rootCreator)
        } applyIf { !delayTemplateSubstitution }
    }

    internal val moduleFragmentInstaller by extending {
        dokkaBase.htmlPreprocessors providing ::ModuleFragmentInstaller applyIf { delayTemplateSubstitution }
    }

    internal val moduleFragmentStrategy by extending {
        templating.templateProcessingStrategy providing ::ModuleFragmentStrategy order {
            before(templating.fallbackProcessingStrategy)
        }
    }

    internal val llmsModuleInstaller by extending {
        dokkaBase.htmlPreprocessors providing ::LlmsModuleInstaller order { after(dokkaBase.rootCreator) }
    }

    internal val llmsModuleFileStrategy by extending {
        templating.templateProcessingStrategy providing ::LlmsModuleFileStrategy order {
            before(templating.fallbackProcessingStrategy)
        }
    }

    internal val llmsIndexInstaller by extending {
        dokkaBase.htmlPreprocessors providing ::LlmsIndexInstaller order {
            after(dokkaBase.rootCreator)
        } applyIf { !delayTemplateSubstitution }
    }

    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement(): PluginApiPreviewAcknowledgement = PluginApiPreviewAcknowledgement
}
