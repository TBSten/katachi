package me.tbsten.katachi.dsl

/**
 * Marks an API that has to be public so that katachi's own internals can reach it across
 * package boundaries, but that is not part of the supported surface. Opt in only when
 * extending katachi itself; it may change or be removed in any release.
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
