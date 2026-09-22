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

/** Every class of katachi itself lives under this package prefix. */
private const val KATACHI_PACKAGE_PREFIX: String = "me.tbsten.katachi."

/**
 * katachi's own tests live under this prefix, which is nested inside
 * [KATACHI_PACKAGE_PREFIX]. They are user code from the DSL's point of view, so their
 * frames must survive the filter below.
 */
private const val KATACHI_TEST_PACKAGE_PREFIX: String = "me.tbsten.katachi.test."

/**
 * A frame belongs to katachi itself when it is under `me.tbsten.katachi.` but not under
 * `me.tbsten.katachi.test.`.
 *
 * Skipping the whole of `me.tbsten.katachi.` would also skip katachi's own specs, and the
 * first surviving frame would then be inside the test runner (observed with kotest:
 * `FreeSpecRootScope.kt:35`). Carving the test prefix back out keeps those specs honest.
 */
private fun isKatachiFrame(className: String): Boolean =
    className.startsWith(KATACHI_PACKAGE_PREFIX) &&
        !className.startsWith(KATACHI_TEST_PACKAGE_PREFIX)

/**
 * Picks the first stack frame outside katachi.
 *
 * Must be reached through a non-inline function. When the call is inlined into user code
 * the frame carries the caller's class and file but a line number remapped past the end of
 * that file, so the reported line would be wrong.
 */
internal fun captureDeclarationSite(): DeclarationSite {
    for (frame in Throwable().stackTrace) {
        if (isKatachiFrame(frame.className)) continue
        return DeclarationSite(
            fileName = frame.fileName ?: DeclarationSite.Unknown.fileName,
            lineNumber = frame.lineNumber,
        )
    }
    return DeclarationSite.Unknown
}
