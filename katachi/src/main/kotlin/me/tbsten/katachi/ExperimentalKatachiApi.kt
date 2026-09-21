package me.tbsten.katachi

/**
 * Marks an API that is meant to be used, but whose shape is still going to change.
 *
 * This is the opposite end of [InternalKatachiApi]. An internal API says "do not touch this";
 * an experimental one says "touch it, and expect to adjust when you upgrade". The processor
 * API is the first thing marked this way: what a processor is handed grows with every version
 * that finds something new to hand it, so freezing that shape at v0.1 would mean freezing it
 * around the two consumers that happen to exist today.
 *
 * The level is [RequiresOptIn.Level.ERROR], the same as [InternalKatachiApi]. A warning would
 * let a project depend on a shape that is going to move without anyone noticing until the
 * upgrade breaks, and "it will change" is not something to find out afterwards.
 *
 * Like [InternalKatachiApi], this exists to make *consumers* of the published `katachi`
 * artifact say so out loud, not to make katachi say it to itself: the `:katachi` module's own
 * `build.gradle.kts` opts every file (main and test) in module-wide, so nothing inside
 * `:katachi` writes `@OptIn` for this. Code outside `:katachi` — the samples, which are
 * separate Gradle builds resolving the published artifact — still has to opt in explicitly,
 * which is what keeps this a real wall rather than a suggestion.
 *
 * ## Example 1: opt in to the processor API from code outside `:katachi`
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.processor.process
 *
 * @OptIn(ExperimentalKatachiApi::class)
 * class PlatformOwnedFilesSpec : FreeSpec({
 *     "platform が持つ役割のファイルだけを集める" {
 *         val files = projectArchitecture.process { model ->
 *             model.roles.filter { it.name.startsWith("Gradle") }.flatMap { model.filesOf(it) }
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
    // `TYPEALIAS` is here and not on `InternalKatachiApi`: an alias is a name a *consumer*
    // reads, so the experimental surface has ones worth naming, while the internal surface
    // has nothing to alias for people who are not supposed to touch it.
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalKatachiApi
