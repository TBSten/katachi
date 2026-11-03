package me.tbsten.katachi.dsl.gradle.internal

import me.tbsten.katachi.dsl.KatachiUnsupportedLayoutScopeException
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutModule
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.ModuleAwareLayoutScope

// Not deprecated, unlike `.module { }`: Gradle.BuildScript is declared for every module katachi finds.
internal fun LayoutScope.expandModules(
    modulePath: String,
    block: LayoutDirectoryScope.() -> Unit,
): LayoutModule {
    val scope = this as? ModuleAwareLayoutScope
        ?: throw KatachiUnsupportedLayoutScopeException(this::class.simpleName ?: "unknown scope")
    return scope.expandModulePath(modulePath, block)
}
