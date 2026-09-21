package me.tbsten.katachi.dsl

/**
 * Marks an API that has to be public so that katachi's own internals can reach it across
 * package boundaries, but that is not part of the supported surface. It may change or be
 * removed in any release.
 *
 * This annotation exists to stop *consumers* of the published `katachi` artifact from
 * depending on internals, not to stop katachi from depending on itself: the `:katachi`
 * module's own `build.gradle.kts` opts every file (main and test) in module-wide, so nothing
 * inside `:katachi` writes `@OptIn` for this. Code outside `:katachi` — the samples
 * (separate Gradle builds that depend on the published artifact), `katachi-konsist`, or any
 * project extending katachi — still has to opt in explicitly, which is what keeps this a real
 * wall rather than a suggestion.
 *
 * ## Example 1: opt in to an internal API from code outside `:katachi`
 * ```kt
 * import me.tbsten.katachi.dsl.InternalKatachiApi
 *
 * @OptIn(InternalKatachiApi::class)
 * class FsPathSpec : FreeSpec({
 *     "区切り文字が続いても1つにまとめる" {
 *         FsPath.of("/repo//app///src").value shouldBe "/repo/app/src"
 *     }
 * })
 * ```
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal katachi API. It may change or be removed without notice.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class InternalKatachiApi
