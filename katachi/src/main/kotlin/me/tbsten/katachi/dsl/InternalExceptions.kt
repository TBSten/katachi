package me.tbsten.katachi.dsl

/**
 * A layout scope reached the Gradle vocabulary without implementing [ModuleAwareLayoutScope].
 *
 * @property actualType the simple name of the class that turned up instead.
 *
 * ## Example 1: report it instead of treating it as a bad definition
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiUnsupportedLayoutScopeException) {
 *     println("katachi bug, scope was ${cause.actualType}. Please report it.")
 * }
 * ```
 */
public class KatachiUnsupportedLayoutScopeException internal constructor(
    public val actualType: String,
) : KatachiInternalException(
    message = """
        The layout scope here is a $actualType, which does not implement ModuleAwareLayoutScope.
        Every scope katachi hands to a `layout { }` block implements it, so a layout cannot
        cause this. Please report it at https://github.com/TBSten/katachi/issues.
    """.trimIndent(),
)

/**
 * A layout key was split into no level at all, so there is no node to hang the declaration on.
 *
 * @property key the layout key as it was written.
 *
 * ## Example 1: report it instead of treating it as a bad definition
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiEmptyLayoutChainException) {
 *     println("katachi bug, key was `${cause.key}`. Please report it.")
 * }
 * ```
 */
public class KatachiEmptyLayoutChainException internal constructor(
    public val key: String,
) : KatachiInternalException(
    message = """
        The layout key `$key` was split into no level at all, so nothing was declared for it.
        Splitting a key on `/` always yields at least one level, so a layout cannot cause
        this. Please report it at https://github.com/TBSten/katachi/issues.
    """.trimIndent(),
)
