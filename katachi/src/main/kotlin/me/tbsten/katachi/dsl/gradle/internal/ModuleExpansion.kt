package me.tbsten.katachi.dsl.gradle.internal

import me.tbsten.katachi.dsl.KatachiUnsupportedLayoutScopeException
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.ModuleAwareLayoutScope
import me.tbsten.katachi.dsl.internal.LayoutModule

// The one way left into module expansion since `.module { }` was removed: Gradle.BuildScript is declared for every
// module katachi finds.
internal fun LayoutScope.expandModules(
    modulePath: String,
    block: LayoutDirectoryScope.() -> Unit,
): LayoutModule {
    val scope = this as? ModuleAwareLayoutScope
        ?: throw KatachiUnsupportedLayoutScopeException(this::class.simpleName ?: "unknown scope")
    return scope.expandModulePath(modulePath, block)
}
