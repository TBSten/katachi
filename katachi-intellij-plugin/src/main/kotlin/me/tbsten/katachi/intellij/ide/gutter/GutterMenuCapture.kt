package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.StateFlow
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.presentation.gutter.GutterMark
import me.tbsten.katachi.intellij.presentation.gutter.MenuAvailability
import me.tbsten.katachi.intellij.presentation.gutter.MenuInput
import me.tbsten.katachi.intellij.presentation.gutter.MenuInputs
import me.tbsten.katachi.intellij.presentation.gutter.MenuModel

/**
 * What a ◆ keeps for its menu. It is built in the background when the ◆ is drawn (the definition's sites are looked up
 * there) and read on the EDT when the ◆ is pressed, which only lays the rows out from it.
 */
internal class CapturedMenu(
    val input: MenuInput,
    val file: VirtualFile,
    /** Where each site the menu can open is; a site not found is [ResolvedSite.Unresolved] (and in [MenuInput.unresolved]). */
    val resolved: Map<DeclarationSiteRef, ResolvedSite>,
    /** The modules whose check is running, read when the menu opens; `null` while nothing provides violations. */
    val runningChecks: StateFlow<Set<ModuleId>>?,
) {
    /** The rows as they are right now: [available] says which platform actions exist, the running checks are read here. */
    fun model(available: MenuAvailability): MenuModel {
        val running = runningChecks?.value?.contains(input.module.id) == true
        return MenuModel.of(input, available.copy(checkRunning = running), BundleGutterStrings)
    }
}

/** Resolves the sites of a ◆'s menu. A file's ◆ share their sites (the role's), so [cache] keeps each answer for the whole file. */
internal object GutterMenuCapture {
    private val EVERYTHING = MenuAvailability(runCheck = true, refresh = true, checkRunning = false)

    fun capture(
        placed: PlacedFile,
        mark: GutterMark,
        file: VirtualFile,
        runningChecks: StateFlow<Set<ModuleId>>?,
        cache: MutableMap<DeclarationSiteRef, ResolvedSite>,
    ): CapturedMenu {
        val bare = MenuInputs.of(mark, placed.roles)
        val sites = MenuModel.of(bare, EVERYTHING, BundleGutterStrings).sites.map { it.site }
        val module = bare.module.id
        val resolved = sites.associateWith { site ->
            cache.getOrPut(site) {
                sdkCall("find ${site.fileName}:${site.line}") { placed.service.resolve(module, site) }
                    .getOrElse { ResolvedSite.Unresolved(it.message ?: it.javaClass.simpleName) }
            }
        }
        val unresolved = resolved.mapNotNull { (site, answer) -> (answer as? ResolvedSite.Unresolved)?.let { site to it.reason } }.toMap()
        return CapturedMenu(bare.copy(unresolved = unresolved), file, resolved, runningChecks)
    }
}
