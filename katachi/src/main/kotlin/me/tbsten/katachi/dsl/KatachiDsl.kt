package me.tbsten.katachi.dsl

/**
 * Marks the receivers of the katachi DSL so that an outer scope is not visible from an
 * inner block. Declaring a group from inside a role block, for example, is rejected at
 * compile time instead of silently attaching the group to the wrong parent.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class KatachiDsl
