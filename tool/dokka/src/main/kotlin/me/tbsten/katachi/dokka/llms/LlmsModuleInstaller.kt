package me.tbsten.katachi.dokka.llms

import me.tbsten.katachi.dokka.LLMS_FILE
import me.tbsten.katachi.dokka.LLMS_FULL_FILE
import me.tbsten.katachi.dokka.katachiConfiguration
import me.tbsten.katachi.dokka.llms.page.PageMarkdown
import me.tbsten.katachi.dokka.llms.page.PageMarkdownWriter
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.pages.ClasslikePageNode
import org.jetbrains.dokka.pages.ContentPage
import org.jetbrains.dokka.pages.MemberPageNode
import org.jetbrains.dokka.pages.ModulePage
import org.jetbrains.dokka.pages.MultimoduleRootPage
import org.jetbrains.dokka.pages.PackagePageNode
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.pages.RendererSpecificResourcePage
import org.jetbrains.dokka.pages.RenderingStrategy
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.pages.WithDocumentables
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.transformers.pages.PageTransformer

/**
 * Puts the llms files and the Markdown pages of a module next to its HTML.
 *
 * - Every page gets its Markdown version at its own URL with `.md` appended
 *   (`-cart/index.html.md`), see [PageMarkdownWriter].
 * - Every page that is a directory — the module, each package, each type — gets an `llms.txt`
 *   and an `llms-full.txt` in that directory, see [LlmsTxtWriter] and [LlmsFullTxtWriter]. The
 *   module's are the ones at its output root.
 *
 * Runs as an HTML preprocessor, where the page tree is final. Where each page is written comes
 * from Dokka's location provider for that tree; the files are added as children of the root with
 * that path as their name, like Dokka's own `styles/…`, and use `DriLocationResolvableWrite`, like
 * its `package-list`, so every link is resolved by the location provider of the run while it
 * renders.
 *
 * In a multi-module build — a run that delays template substitution — the links are not final:
 * a link to the module's own page is left as a token and a link to another module's declaration
 * as a DRI, the way Dokka's HTML leaves a `ResolveLinkCommand` (see [DeferredLinks]). The
 * aggregating run fills them in while it copies the files into the module's directory
 * (see `LlmsModuleFileStrategy`). `baseUrl` is therefore used only by a run that is not delayed.
 */
internal class LlmsModuleInstaller(private val context: DokkaContext) : PageTransformer {
    private val configuration by lazy { context.katachiConfiguration() }
    private val factory by lazy {
        LlmsModelFactory(
            signatureProvider = context.plugin<DokkaBase>().querySingle { signatureProvider },
            optionalPackages = configuration.optionalPackagePatterns.map(::Regex),
        )
    }
    /** The llms files link to the Markdown pages, unless those are turned off. */
    private val linkedPage by lazy { if (configuration.pageMarkdown) LinkedPage.Markdown else LinkedPage.Html }
    private val style by lazy {
        if (context.configuration.delayTemplateSubstitution) LlmsLinkStyle.Deferred else LlmsLinkStyle.Final(LinkBase(configuration.baseUrl))
    }

    override fun invoke(input: RootPageNode): RootPageNode {
        if (!configuration.llms && !configuration.llmsFull && !configuration.pageMarkdown) return input
        if (input.withDescendants().any { it is MultimoduleRootPage }) return input
        val modulePage = input.withDescendants().filterIsInstance<ModulePage>().firstOrNull() ?: return input
        val module = factory.of(modulePage) ?: return input
        val scopes = ScopeIndex(module, factory)
        val locations = context.plugin<DokkaBase>().querySingle { locationProviderFactory }.getLocationProvider(input)

        val files = mutableListOf<PageNode>()
        fun visit(page: PageNode, parent: LlmsScope?) {
            val scope = scopes.scopeOf(page)
            val path = if (page is ContentPage && scope != null) locations.resolve(page, null, false) else null
            if (scope != null && path != null) files += filesOf(page, scope, parent, path, scopes)
            page.children.forEach { visit(it, scope ?: parent) }
        }
        visit(input, null)
        return input.modified(name = input.name, children = input.children + files)
    }

    private fun filesOf(page: PageNode, scope: LlmsScope, parent: LlmsScope?, path: String, scopes: ScopeIndex): List<PageNode> {
        val directory = directoryOf(path)
        val isDirectory = page is ModulePage || page is PackagePageNode || page is ClasslikePageNode
        return listOfNotNull(
            file(joinPath(directory, LLMS_FILE), directory) { links ->
                LlmsTxtWriter(configuration.featuredTitle).write(scope, links)
            }.takeIf { isDirectory && configuration.llms },
            file(joinPath(directory, LLMS_FULL_FILE), directory) { links ->
                LlmsFullTxtWriter(configuration.featuredTitle).write(scope, links)
            }.takeIf { isDirectory && configuration.llmsFull },
            file(markdownPathOf(path), directory) { links ->
                PageMarkdownWriter(configuration.featuredTitle).write(markdownOf(page, scope, parent, scopes), links)
            }.takeIf { configuration.pageMarkdown },
        )
    }

    private fun markdownOf(page: PageNode, scope: LlmsScope, parent: LlmsScope?, scopes: ScopeIndex) = PageMarkdown(
        scope = scope,
        declarations = when (page) {
            is MemberPageNode -> scopes.declarationsOf(page)
            else -> listOfNotNull(scope as? LlmsDeclaration)
        },
        parent = parent,
        children = page.children.flatMap { child ->
            if (child is MemberPageNode) scopes.declarationsOf(child) else listOfNotNull(scopes.scopeOf(child))
        },
        featured = (scope as? LlmsModule)?.featured.orEmpty(),
    )

    private fun file(path: String, directory: String, write: (LlmsLinks) -> String) =
        RendererSpecificResourcePage(
            path,
            emptyList(),
            RenderingStrategy.DriLocationResolvableWrite { resolver -> write(LlmsLinks(resolver, style, linkedPage, directory)) },
        )
}

/**
 * The [LlmsScope] of each page, found by the DRI of its declaration in the module's model, which
 * is built once for the whole module.
 */
private class ScopeIndex(private val module: LlmsModule, private val factory: LlmsModelFactory) {
    private val byDri: Map<DRI, LlmsScope> = buildMap {
        module.packages.forEach { pkg ->
            put(pkg.target.dri, pkg)
            (pkg.functions + pkg.properties).forEach { put(it.target.dri, it) }
            pkg.types.forEach { type ->
                put(type.target.dri, type)
                type.members.forEach { putIfAbsent(it.target.dri, it) }
            }
        }
    }

    /** The scope of [page]; for a member page, its first overload. Null for pages without declarations. */
    fun scopeOf(page: PageNode): LlmsScope? = when (page) {
        is ModulePage -> module
        is MemberPageNode -> declarationsOf(page).firstOrNull()
        is ClasslikePageNode -> page.documentables.firstOrNull()?.let { byDri[it.dri] } ?: factory.of(page)
        is WithDocumentables -> page.documentables.firstOrNull()?.let { byDri[it.dri] }
        else -> null
    }

    fun declarationsOf(page: MemberPageNode): List<LlmsDeclaration> = page.documentables.mapNotNull { documentable ->
        byDri[documentable.dri] as? LlmsDeclaration ?: factory.declarationOf(documentable)
    }
}
