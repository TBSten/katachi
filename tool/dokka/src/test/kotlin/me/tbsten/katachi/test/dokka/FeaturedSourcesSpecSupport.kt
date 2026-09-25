package me.tbsten.katachi.test.dokka

/**
 * Inline sources for the specs, in the format of Dokka's `testInline`: each file starts with a
 * line holding its path.
 *
 * Also the smallest example of what the plugin is for: which declarations can carry `@featured`,
 * and what the summary is taken from.
 */
internal object FeaturedSources {
    /** Every kind of declaration the tag can be put on, plus the cases that must be left out. */
    val ALL_KINDS: String = """
        |/src/main/kotlin/sample/Architecture.kt
        |package sample
        |
        |/**
        | * Declares the architecture of a project.
        | *
        | * The second paragraph is not part of the summary.
        | *
        | * @featured
        | */
        |public fun architecture(): Architecture = Architecture()
        |
        |/**
        | * The architecture as a value.
        | *
        | * @featured
        | */
        |public class Architecture {
        |    /**
        |     * Checks the architecture.
        |     *
        |     * @featured Runs every check and throws `AssertionError` on violations.
        |     */
        |    public fun assert(maxViolations: Int) {}
        |
        |    /**
        |     * An overload on the same page, so it is listed once.
        |     *
        |     * @featured Runs every check and throws `AssertionError` on violations.
        |     */
        |    public fun assert() {}
        |
        |    /** Not tagged, so never listed. */
        |    public fun describe(): String = ""
        |}
        |
        |/**
        | * The version of the definition format.
        | *
        | * @featured
        | */
        |public val formatVersion: Int = 1
        |
        |/** @featured */
        |public typealias Definition = Architecture
        |
        |/** How strict a check is. */
        |public enum class Strictness {
        |    /**
        |     * Every violation fails.
        |     *
        |     * @featured
        |     */
        |    STRICT,
        |    LENIENT,
        |}
        |
        |/**
        | * Hidden from the documentation, so the tag has nothing to point at.
        | *
        | * @featured
        | */
        |internal class Hidden
        |
        |/** Documented, but not featured. */
        |public class Untagged
        |
        |/src/main/kotlin/sample/dsl/Scope.kt
        |package sample.dsl
        |
        |/**
        | * Where roles are declared.
        | *
        | * @featured
        | */
        |public interface DeclarationScope {
        |    /** Holds the shared settings. */
        |    public object Defaults {
        |        /**
        |         * The name used when none is given.
        |         *
        |         * @featured
        |         */
        |        public val name: String = "default"
        |    }
        |}
    """.trimMargin()

    /** A module whose KDoc never mentions the tag. */
    val NONE: String = """
        |/src/main/kotlin/plain/Plain.kt
        |package plain
        |
        |/** Nothing here is featured. */
        |public class Plain
    """.trimMargin()

    /** The names [ALL_KINDS] is expected to list, in the order they are expected in. */
    val ALL_KINDS_NAMES: List<String> = listOf(
        "Architecture",
        "Architecture.assert",
        "Definition",
        "Strictness.STRICT",
        "architecture",
        "formatVersion",
        "DeclarationScope",
        "DeclarationScope.Defaults.name",
    )

    /** A single-file module with one featured class, for the multi-module specs. */
    fun module(packageName: String, className: String, summary: String): String = """
        |/src/main/kotlin/${packageName.replace('.', '/')}/$className.kt
        |package $packageName
        |
        |/**
        | * $summary
        | *
        | * @featured
        | */
        |public class $className
    """.trimMargin()
}
