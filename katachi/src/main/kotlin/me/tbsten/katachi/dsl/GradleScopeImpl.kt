package me.tbsten.katachi.dsl

import java.util.EnumMap
import me.tbsten.katachi.dsl.internal.GradleRoleKind
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.declareGroupAt
import me.tbsten.katachi.dsl.internal.declareRoleAt
import org.intellij.lang.annotations.Language

/**
 * What the `gradle { }` block writes, kept until katachi has declared its own roles.
 *
 * Metadata goes straight to [group]. A hook only records its block, read back by
 * [hooksOf] while the role it names is declared. A role or group of the user's own takes its
 * declaration site now, while the user's frame is still on the stack, and is declared by
 * [declareDeferred] afterwards, so that katachi's roles come first and a clash with one of
 * them reports katachi's as the first declaration.
 */
internal class GradleScopeImpl(internal val group: GroupScope) : GradleScope {
    private val hooks = EnumMap<GradleRoleKind, MutableList<GradleRoleScope.() -> Unit>>(GradleRoleKind::class.java)
    private val deferred = mutableListOf<() -> Unit>()

    @get:Language("markdown")
    @set:Language("markdown")
    override var title: String
        get() = group.title
        set(value) {
            group.title = value
        }

    @get:Language("markdown")
    @set:Language("markdown")
    override var summary: String?
        get() = group.summary
        set(value) {
            group.summary = value
        }

    @get:Language("markdown")
    @set:Language("markdown")
    override var description: String?
        get() = group.description
        set(value) {
            group.description = value
        }

    override var documented: Boolean
        get() = group.documented
        set(value) {
            group.documented = value
        }

    override fun String.group(block: GroupScope.() -> Unit) {
        val name = this
        val declaredAt = captureDeclarationSite()
        deferred += { group.declareGroupAt(name, declaredAt, block) }
    }

    override fun String.role(block: RoleScope.() -> Unit) {
        val name = this
        val declaredAt = captureDeclarationSite()
        deferred += { group.declareRoleAt(name, declaredAt, block) }
    }

    override fun launcherScript(block: GradleRoleScope.() -> Unit) = record(GradleRoleKind.LauncherScript, block)
    override fun wrapperJar(block: GradleRoleScope.() -> Unit) = record(GradleRoleKind.WrapperJar, block)
    override fun wrapperProperties(block: GradleRoleScope.() -> Unit) = record(GradleRoleKind.WrapperProperties, block)
    override fun settingsScript(block: GradleRoleScope.() -> Unit) = record(GradleRoleKind.SettingsScript, block)
    override fun buildScript(block: GradleRoleScope.() -> Unit) = record(GradleRoleKind.BuildScript, block)
    override fun gradleProperties(block: GradleRoleScope.() -> Unit) = record(GradleRoleKind.GradleProperties, block)
    override fun versionCatalog(block: GradleRoleScope.() -> Unit) = record(GradleRoleKind.VersionCatalog, block)
    override fun daemonJvmProperties(block: GradleRoleScope.() -> Unit) = record(GradleRoleKind.DaemonJvmProperties, block)

    private fun record(kind: GradleRoleKind, block: GradleRoleScope.() -> Unit) {
        hooks.getOrPut(kind) { mutableListOf() } += block
    }

    /** The blocks written for [kind], in the order they were written. */
    fun hooksOf(kind: GradleRoleKind): List<GradleRoleScope.() -> Unit> = hooks[kind].orEmpty()

    /** Declares the roles and groups the block wrote, in the order it wrote them. */
    fun declareDeferred() {
        deferred.forEach { it() }
    }
}

/** A hook's view of one role `gradle()` declares: its constraints and metadata, nothing else. */
internal class GradleRoleScopeImpl(internal val role: RoleScope) : GradleRoleScope {
    override fun fileConstraint(
        name: String?,
        declaredAt: DeclarationSite,
        scope: FileConstraintRange,
        check: FileConstraint,
    ) {
        role.fileConstraint(name, declaredAt, scope, check)
    }
}
