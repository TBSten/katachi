package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.captureDeclarationSite

/**
 * Where a declaration was written, as `FileName.kt:42`.
 *
 * Captured while the DSL is evaluated so that a violation reported much later can still
 * point back at the line the user wrote.
 *
 * ## Example 1: print a declaration site
 * ```kt
 * DeclarationSite("ProjectArchitecture.kt", 42).toString() shouldBe "ProjectArchitecture.kt:42"
 * ```
 *
 * @property fileName the name of the file alone, without its directory, such as
 *   `ProjectArchitecture.kt`.
 * @property lineNumber the line in that file, counted from 1.
 * @constructor Takes the file name and the line. katachi captures one by itself for every
 *   declaration; build one by hand to compare against it in a test.
 */
public data class DeclarationSite(
    public val fileName: String,
    public val lineNumber: Int,
) {
    /** `<fileName>:<lineNumber>`, the form a report prints next to a declaration. */
    override fun toString(): String = "$fileName:$lineNumber"

    /**
     * Where a [DeclarationSite] comes from: [capture] for the line that called into the DSL, and
     * [Unknown] as the stand-in when the real one could not be captured.
     *
     * ## Example 1: fall back when there is no real site
     * ```kt
     * val site = DeclarationSite.Unknown
     * ```
     */
    public companion object {
        /**
         * Used when the call site cannot be determined from the stack trace.
         *
         * ## Example 1: read the fallback value
         * ```kt
         * DeclarationSite.Unknown.fileName shouldBe "<unknown>"
         * ```
         */
        public val Unknown: DeclarationSite = DeclarationSite("<unknown>", -1)

        /**
         * The line that called into katachi: the first stack frame outside `me.tbsten.katachi.`.
         *
         * katachi's own artifacts -- `katachi-konsist` included -- are inside, so a DSL function
         * one of them adds can call this from inside itself and get the line its user wrote, which
         * is what a report should point back at. Everything else is outside: katachi's own specs
         * (`me.tbsten.katachi.test.`), and code in any other package, so a wrapper of your own gets
         * its own line from this -- take the site from your caller instead, as
         * `fileConstraint(declaredAt = ...)` does. [Unknown] when no frame is outside.
         *
         * Not `inline`, and it must not be reached through an `inline` function either: a frame
         * inlined into its caller carries a line number remapped past the end of the file.
         *
         * ## Example 1: remember the line that declared a rule
         * ```kt
         * // ProjectArchitecture.kt
         * val declaredAt = DeclarationSite.capture()
         * declaredAt.fileName shouldBe "ProjectArchitecture.kt"
         * ```
         */
        @ExperimentalKatachiApi
        public fun capture(): DeclarationSite = captureDeclarationSite()
    }
}
