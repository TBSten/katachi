package me.tbsten.katachi

/**
 * Marks an API that is public only so that katachi's own other modules can reach it.
 *
 * Those are `katachi-konsist`, the code the Gradle plugin generates, `tool/dokka`, katachi's own
 * architecture test and samples. It is not part of the supported surface for anyone else, and
 * it may change or be removed in any release.
 *
 * An API a project is meant to use while its shape still moves is [ExperimentalKatachiApi],
 * not this. What is not used from any other module is `internal`, not this.
 *
 * The level is [RequiresOptIn.Level.ERROR], so any code outside `:katachi`, including
 * katachi's own other modules, has to opt in explicitly.
 *
 * ## Example 1: opt in from one of katachi's own modules
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.InternalKatachiApi
 * import me.tbsten.katachi.check.validate
 * import me.tbsten.katachi.dsl.files.internal.RealFileSystem
 *
 * // katachi-konsist's own spec, checking a fixture project on disk.
 * @OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
 * class KonsistFixtureSpec : FreeSpec({
 *     "finds the broken constraint" {
 *         projectArchitecture.validate(RealFileSystem(fixtureRoot)).shouldNotBeEmpty()
 *     }
 * })
 * ```
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal katachi API. It may change or be removed without notice. " +
        "Prefer the public API; if you must use it, opt in with @OptIn(InternalKatachiApi::class) on the caller, " +
        "@file:OptIn(InternalKatachiApi::class) at the top of the file, " +
        "or the compiler option -opt-in=me.tbsten.katachi.InternalKatachiApi.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class InternalKatachiApi
