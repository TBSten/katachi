package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.data.baseline.BaselineReading
import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.presentation.rules.NoRoleDecision
import me.tbsten.katachi.intellij.presentation.rules.NoRoleInput
import me.tbsten.katachi.intellij.presentation.rules.ResolvedSiteView
import me.tbsten.katachi.intellij.presentation.rules.decideNoRoleNotification
import me.tbsten.katachi.intellij.presentation.rules.shouldEnsureDefinitionLoaded
import java.nio.file.Path

/** A file the "no role holds this file" notification shows for, and what its clicks reach (the `VirtualFile`s [decision] cannot hold). */
internal class NoRoleFound(
    val decision: NoRoleDecision.NoRole,
    /** Where each of [NoRoleDecision.NoRole.nearby] is declared, position by position. */
    val nearbySites: List<ResolvedSite>,
    /** Where `architecture { }` is written. */
    val architectureSite: ResolvedSite?,
)

/**
 * Gathers [NoRoleInput] for [file] and decides. Runs in the background under a read lock: `forFile` and the
 * declaration lookups (`resolve`) are for the background thread (design 4). `null`: no notification. A file that
 * some role holds ends here after one cached `forFile`, without asking the VCS or resolving anything.
 *
 * Side effects, all of the platform's kind: it starts the definition's load when nothing was read (unless the user
 * turned loading without a user action off), tells the memory to recompute all notifications whenever the definition's
 * state changes, and, while the file shows the notification, to recompute it when its VCS status changes.
 *
 * ```kotlin
 * val found = decideNoRole(ports, file, path) ?: return null
 * found.decision.nearby  // the rows of the popup
 * ```
 */
internal fun decideNoRole(ports: RulesPorts, file: VirtualFile, path: Path): NoRoleFound? {
    val settings = ports.settings()
    if (!settings.notificationsEnabled || !settings.notifyOnNoRole) return null
    // Only a project with a definition module gets the service (decision 17).
    val service = ports.definitions() ?: return null
    val memory = ports.memory()
    // The state this decision starts from: what the memory must not draw again when it starts to follow the service.
    val observed = service.state.value
    memory.followDefinitions(service, observed)
    // Read actions restart: ensureLoaded loads once however often it is called.
    if (shouldEnsureDefinitionLoaded(observed, settings.loadWithoutUser)) service.ensureLoaded()

    val roles = service.forFile(file)
    val unexpected = roles?.outcome.let { it == MatchOutcome.Unexpected || it is MatchOutcome.UnexpectedUnder || it is MatchOutcome.CaptureMismatch }
    if (roles == null || !unexpected || memory.current.isNoRoleDismissed(path)) {
        memory.unfollowVcs(file)
        return null
    }

    val module = roles.module.id
    val architecture = service.definitionOf(module)?.definition?.architecture
    val nearbySites = roles.nearby.map { service.resolveOrNot(module, it.role.declaredAt) }
    val architectureSite = service.resolveOrNot(module, architecture?.declaredAt)
    val vcs = ports.vcs(file)
    val baseline = baselineOf(ports, roles.projectRoot, architecture)
    val decision = decideNoRoleNotification(
        NoRoleInput(
            file = path,
            roles = roles,
            definition = observed,
            architecture = architecture,
            vcs = vcs,
            settings = settings,
            memory = memory.current,
            nearbySites = nearbySites.map(::viewOf),
            architectureSite = viewOf(architectureSite),
            baseline = baseline,
        ),
    )
    val noRole = decision as? NoRoleDecision.NoRole
    if (noRole == null) {
        memory.unfollowVcs(file)
        return null
    }
    memory.followVcs(file, vcs) { ports.vcsIfSettled(file) }
    return NoRoleFound(noRole, nearbySites, architectureSite)
}

/** The baseline the definition names, read through [ports]; a definition without `architecture { }` is [BaselineReading.Missing], so the wording stays conditional. */
private fun baselineOf(ports: RulesPorts, projectRoot: Path, architecture: ArchitectureModel?): BaselineReading {
    architecture ?: return BaselineReading.Missing
    val name = architecture.baseline ?: return BaselineReading.NotConfigured
    return ports.baseline(projectRoot, name)
}

private fun DefinitionModelService.resolveOrNot(module: ModuleId, site: DeclarationSiteRef?): ResolvedSite =
    if (site == null) ResolvedSite.Unresolved("the declaration was not captured in the definition") else resolve(module, site)

private fun viewOf(site: ResolvedSite): ResolvedSiteView = when (site) {
    is ResolvedSite.Found -> ResolvedSiteView.Found(site.file.name, site.line)
    is ResolvedSite.Unresolved -> ResolvedSiteView.Unresolved(site.reason)
}
