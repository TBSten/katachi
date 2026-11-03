package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiInternalException

/**
 * A layout scope reached the Gradle vocabulary without knowing which module it is in; this is a bug in katachi.
 *
 * ## Example 1: report it instead of treating it as a bad definition
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiUnsupportedLayoutScopeException) {
 *     println("katachi bug, scope was ${cause.actualType}. Please report it.")
 * }
 * ```
 *
 * @property actualType the simple name of the class that turned up instead.
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
 * A layout key was split into no level at all; this is a bug in katachi.
 *
 * ## Example 1: report it instead of treating it as a bad definition
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiEmptyLayoutChainException) {
 *     println("katachi bug, key was `${cause.key}`. Please report it.")
 * }
 * ```
 *
 * @property key the layout key as it was written.
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

/**
 * A path recorded a second `.template { }` with no first one to compare it against; this is a bug in katachi.
 *
 * ## Example 1: report it instead of treating it as a bad definition
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiMissingFirstTemplateException) {
 *     println("katachi bug, path was `${cause.path}`. Please report it.")
 * }
 * ```
 *
 * @property path the declared path of the node this was noticed on.
 * @property role the role evaluating that node, qualified.
 */
@ExperimentalKatachiApi
public class KatachiMissingFirstTemplateException internal constructor(
    public val path: String,
    public val role: String,
) : KatachiInternalException(
    message = """
        Path "$path" of role "$role" recorded a second `.template { }` to reject, but the first
        one it was meant to conflict with is gone. `.template { }` only ever records a conflict
        after finding a first template already on the same node, so a layout cannot cause this.
        Please report it at https://github.com/TBSten/katachi/issues.
    """.trimIndent(),
)

/**
 * A capture carried a naming conversion katachi does not know; this is a bug in katachi.
 *
 * Only katachi's own conversions (`camelCase`, `pascalCase` and the like) can be applied to a
 * capture, so no definition can cause this.
 *
 * ## Example 1: report it rather than work around it
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiUnknownCaptureTransformException) {
 *     println("katachi bug, conversion was `${cause.spelling}`. Please report it.")
 * }
 * ```
 *
 * @property name the capture's name.
 * @property spelling the conversion the token named.
 */
@ExperimentalKatachiApi
public class KatachiUnknownCaptureTransformException internal constructor(
    public val name: String,
    public val spelling: String,
) : KatachiInternalException(
    message = """
        capture("$name") carries a conversion ".$spelling" that katachi has no naming conversion for.
        Only katachi's own conversions (camelCase, pascalCase, kebabCase, snakeCase, screamingSnakeCase,
        flatCase) write a conversion into a capture token, so a layout cannot cause this.
        Please report it at https://github.com/TBSten/katachi/issues.
    """.trimIndent(),
)
