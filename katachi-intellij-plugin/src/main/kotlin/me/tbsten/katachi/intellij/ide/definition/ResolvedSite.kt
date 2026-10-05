package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.vfs.VirtualFile

/**
 * Where a [me.tbsten.katachi.intellij.model.DeclarationSiteRef] (a file name and a line) is in the project,
 * found among the test source roots of the definition module (design 4.6). It only holds the answer; the
 * resolver that walks the source roots wraps its SDK calls in `sdkCall`.
 *
 * ```kotlin
 * when (val site = service.resolve(module, ref)) {
 *     is ResolvedSite.Found -> open(site.file, site.line)
 *     is ResolvedSite.Unresolved -> showWithoutLink(site.reason)
 * }
 * ```
 */
internal sealed interface ResolvedSite {
    /** Exactly one file of that name; [line] is 1-based, as the JSON has it. */
    data class Found(val file: VirtualFile, val line: Int) : ResolvedSite

    /** No file of that name, several (a Kotlin Multiplatform project's same-named files), no source root, or the site was not captured. */
    data class Unresolved(val reason: String) : ResolvedSite
}
