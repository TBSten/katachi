package me.tbsten.katachi.dokka.llms

import me.tbsten.katachi.dokka.LLMS_FILE
import me.tbsten.katachi.dokka.LLMS_FULL_FILE
import me.tbsten.katachi.dokka.katachiConfiguration
import org.jetbrains.dokka.DokkaConfiguration.DokkaModuleDescription
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.templates.TemplateProcessingStrategy
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Finishes the links of a module's llms files and Markdown pages while the aggregating run copies
 * them into the module's directory — the llms counterpart of the `ResolveLinkCommand`s Dokka fills
 * in the HTML.
 *
 * The module's run left its links unfinished (see [DeferredLinks]). Here a link to the module's
 * own page is made relative to the file it is in, or absolute with the configured `baseUrl`, the
 * public URL of the aggregated output root; a link to another module's declaration is resolved
 * with [ModuleLinkResolver]. A link no module can resolve is written as its label, and a warning
 * names it, as Dokka's HTML writes such a link as plain text.
 */
internal class LlmsModuleFileStrategy(private val context: DokkaContext) : TemplateProcessingStrategy {
    private val base by lazy { LinkBase(context.katachiConfiguration().baseUrl) }
    private val resolver by lazy { ModuleLinkResolver(context) }
    private val warned: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun process(input: File, output: File, moduleContext: DokkaModuleDescription?): Boolean {
        if (moduleContext == null || !isLlmsFile(input)) return false
        val moduleOutput = moduleContext.sourceOutputDirectory.absoluteFile.normalize()
        val relative = input.absoluteFile.normalize().relativeToOrNull(moduleOutput)
            ?.takeUnless { it.invariantSeparatorsPath.startsWith("..") }
            ?: return false

        val moduleDirectory = moduleContext.directoryIn(context.configuration.outputDir)
        // Where the file ends up in the aggregated output, which its relative links start from.
        val fileDirectory = joinPath(moduleDirectory, directoryOf(relative.invariantSeparatorsPath))
        val rewritten = DeferredLinks.rewrite(
            text = input.readText(),
            pathLink = { path -> base.link(joinPath(moduleDirectory, path), fileDirectory) },
            resolveDri = { dri, page -> resolver.resolve(dri, page)?.let { base.link(it, fileDirectory) } },
        )
        // The same declaration is linked from many files; each is named once, where first met.
        val newlyUnresolved = rewritten.unresolved.distinct().filter { warned.add(it) }
        if (newlyUnresolved.isNotEmpty()) {
            context.logger.warn(
                "katachi-dokka: ${newlyUnresolved.size} declaration(s) linked from ${input.path} (and " +
                    "possibly other llms files) are documented by no module of this build, so the " +
                    "links are written as plain text: ${newlyUnresolved.take(MAX_NAMED).joinToString()}",
            )
        }
        output.parentFile?.mkdirs()
        output.writeText(rewritten.text)
        return true
    }

    private fun isLlmsFile(file: File): Boolean = file.name in LLMS_FILES || file.name.endsWith(MARKDOWN_SUFFIX)

    private companion object {
        val LLMS_FILES = setOf(LLMS_FILE, LLMS_FULL_FILE)
        const val MAX_NAMED = 5
    }
}
