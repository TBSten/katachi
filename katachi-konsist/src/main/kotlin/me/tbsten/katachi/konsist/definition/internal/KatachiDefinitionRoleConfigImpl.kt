package me.tbsten.katachi.konsist.definition.internal

import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.definition.KatachiDefinitionConfig
import me.tbsten.katachi.konsist.definition.KatachiDefinitionRoleConfig

internal class KatachiDefinitionRoleConfigImpl(
    override var name: String,
    override var fileName: String,
) : KatachiDefinitionRoleConfig.Scope {
    override var enabled: Boolean = true
    override var recommendedContent: Boolean = true
    override var content: (KtFileContentScope.() -> Unit)? = null
        private set
    override var additional: RoleScope.() -> Unit = {}
        private set

    override fun content(block: KtFileContentScope.() -> Unit) {
        val before = content
        content = if (before == null) block else {
            { before(); block() }
        }
    }

    override fun additional(block: RoleScope.() -> Unit) {
        val before = additional
        additional = { before(); block() }
    }
}

internal class KatachiDefinitionConfigImpl(
    override val rootPackage: String,
    override val moduleDir: String,
    override val entry: KatachiDefinitionRoleConfig,
    override val group: KatachiDefinitionRoleConfig,
    override val role: KatachiDefinitionRoleConfig,
    override val architectureTest: KatachiDefinitionRoleConfig,
) : KatachiDefinitionConfig
