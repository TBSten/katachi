package me.tbsten.katachi.dsl.gradle.internal

import me.tbsten.katachi.dsl.KatachiUnsupportedLayoutScopeException
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutModule
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.ModuleAwareLayoutScope

/**
 * Runs [block] once per module [modulePath] stands for, the way `.module { }` does.
 *
 * What `gradle()` declares the build scripts with. Unlike the public `.module { }` and
 * `expandModulePath`, this is not deprecated: `Gradle.BuildScript` is declared for every module
 * katachi finds, after the sugar is gone too.
 */
internal fun LayoutScope.expandModules(
    modulePath: String,
    block: LayoutDirectoryScope.() -> Unit,
): LayoutModule {
    val scope = this as? ModuleAwareLayoutScope
        ?: throw KatachiUnsupportedLayoutScopeException(this::class.simpleName ?: "unknown scope")
    return scope.expandModulePath(modulePath, block)
}
