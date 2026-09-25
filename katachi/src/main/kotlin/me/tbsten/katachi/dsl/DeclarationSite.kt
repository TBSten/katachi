package me.tbsten.katachi.dsl

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
 */
public data class DeclarationSite(
    public val fileName: String,
    public val lineNumber: Int,
) {
    override fun toString(): String = "$fileName:$lineNumber"

    /**
     * Where a stand-in [DeclarationSite] comes from when the real one could not be captured:
     * [Unknown].
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
    }
}
