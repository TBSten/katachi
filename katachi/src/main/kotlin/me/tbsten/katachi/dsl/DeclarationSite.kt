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
     * Where a [DeclarationSite] comes from.
     *
     * [capture] for the line that called into the DSL, and [Unknown] as the stand-in when the
     * real one could not be captured.
     *
     * ## Example 1: capture the current line, or fall back when there is no real site
     * ```kt
     * // ProjectArchitecture.kt
     * val declaredAt = DeclarationSite.capture()
     * declaredAt.fileName shouldBe "ProjectArchitecture.kt"
     *
     * DeclarationSite.Unknown.fileName shouldBe "<unknown>"
     * ```
     */
    public companion object {
        /** Used when the call site cannot be determined from the stack trace. */
        public val Unknown: DeclarationSite = DeclarationSite("<unknown>", -1)

        /**
         * The line that called into katachi: the first stack frame outside `me.tbsten.katachi.`.
         *
         * Called from a wrapper of your own, it returns the wrapper's line; take the site from
         * your caller instead, as `fileConstraint(declaredAt = ...)` does. Do not call it through
         * an `inline` function, whose line numbers are remapped. [Unknown] when no frame is outside.
         */
        @ExperimentalKatachiApi
        public fun capture(): DeclarationSite = captureDeclarationSite()
    }
}
