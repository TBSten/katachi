package me.tbsten.katachi

/**
 * Marks an API that is public only so that katachi's own other modules can reach it —
 * `katachi-konsist`, the code the Gradle plugin generates, `tool/dokka`, katachi's own
 * architecture test and samples. It is not part of the supported surface for anyone else, and
 * it may change or be removed in any release.
 *
 * An API a project is meant to use while its shape still moves is [ExperimentalKatachiApi],
 * not this. What is not used from any other module is `internal`, not this.
 *
 * A top-level declaration carrying this annotation lives in a `.internal` package
 * (`me.tbsten.katachi.check.internal` and the like), next to the `internal` ones.
 *
 * This annotation exists to stop *consumers* of the published `katachi` artifact from
 * depending on internals, not to stop katachi from depending on itself: the `:katachi`
 * module's own `build.gradle.kts` opts every file (main and test) in module-wide, so nothing
 * inside `:katachi` writes `@OptIn` for this. Code outside `:katachi` — including katachi's
 * own other modules — still has to opt in explicitly, which is what keeps this a real wall
 * rather than a suggestion.
 *
 * ## Example 1: opt in from one of katachi's own modules
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.InternalKatachiApi
 * import me.tbsten.katachi.check.internal.report
 * import me.tbsten.katachi.check.internal.validate
 * import me.tbsten.katachi.fs.internal.RealFileSystem
 *
 * // katachi-konsist's own spec, reading the report of a fixture project.
 * @OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
 * class KonsistReportSpec : FreeSpec({
 *     "names the broken constraint" {
 *         projectArchitecture.validate(RealFileSystem(fixtureRoot)).report() shouldContain "must"
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
