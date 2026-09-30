package me.tbsten.katachi

/**
 * Marks an API that is meant to be used, but whose shape is still going to change.
 *
 * This is the opposite end of [InternalKatachiApi]. An internal API is only for katachi's own
 * other modules and says "do not touch this"; an experimental one is open to every project and
 * says "touch it, and expect to adjust when you upgrade". The processor API is marked this
 * way, and so are the template DSL (`.template { }` and `capture()`), documentation
 * generation, and what a project needs to extend katachi on its own terms — its own
 * [me.tbsten.katachi.dsl.FileSelection], or its own `.module { }` built on
 * [me.tbsten.katachi.dsl.gradle.expandModulePath]. What a processor is handed grows with every
 * version that finds something new to hand it, so freezing that shape now would mean freezing
 * it around the consumers that happen to exist today.
 *
 * The level is [RequiresOptIn.Level.ERROR], the same as [InternalKatachiApi]. A warning would
 * let a project depend on a shape that is going to move without anyone noticing until the
 * upgrade breaks, and "it will change" is not something to find out afterwards.
 *
 * Like [InternalKatachiApi], this exists to make *consumers* of the published `katachi`
 * artifact say so out loud, not to make katachi say it to itself: the `:katachi` module's own
 * `build.gradle.kts` opts every file (main and test) in module-wide, so nothing inside
 * `:katachi` writes `@OptIn` for this. Code outside `:katachi` still has to opt in
 * explicitly, which is what keeps this a real wall rather than a suggestion.
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
    message = "This katachi API is experimental. It is safe to use, but its shape will still change.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class ExperimentalKatachiApi
