package me.tbsten.katachi

/**
 * Marks an API that is meant to be used, but whose shape is still going to change.
 *
 * Unlike [InternalKatachiApi], which says "do not touch this", an experimental API is open to
 * every project and says "use it, and expect to adjust when you upgrade". The processor API,
 * the template DSL (`.template { }` and `capture()`), documentation generation, and the
 * extension points are marked this way: a [FileConstraint][me.tbsten.katachi.dsl.FileConstraint]
 * of your own, and the [KatachiFileSystem][me.tbsten.katachi.dsl.files.KatachiFileSystem] that
 * a [FileSelection][me.tbsten.katachi.dsl.FileSelection] of your own returns
 * (`FileSelection` itself is not experimental).
 *
 * The level is [RequiresOptIn.Level.ERROR], so code outside `:katachi` has to opt in
 * explicitly with `@OptIn(ExperimentalKatachiApi::class)`.
 *
 * ## Example 1: opt in to the processor API from code outside `:katachi`
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.processor.process
 *
 * @OptIn(ExperimentalKatachiApi::class)
 * class PlatformOwnedFilesSpec : FreeSpec({
 *     "collects only the files of roles the platform owns" {
 *         val files = projectArchitecture.process { context ->
 *             context.roles.filter { it.name.startsWith("Gradle") }.flatMap { context.filesOf(it) }
 *         }
 *         files shouldContain "settings.gradle.kts"
 *     }
 * })
 * ```
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This katachi API is experimental. It is safe to use, but its shape will still change. " +
        "To use it anyway, opt in with @OptIn(ExperimentalKatachiApi::class) on the caller, " +
        "@file:OptIn(ExperimentalKatachiApi::class) at the top of the file, " +
        "or the compiler option -opt-in=me.tbsten.katachi.ExperimentalKatachiApi.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class ExperimentalKatachiApi
