package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite

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
 * Whether [block] was compiled into katachi itself, such as a `layout { }` of a role
 * [me.tbsten.katachi.dsl.gradle.gradle] declares, rather than written by the user.
 *
 * Such a block is evaluated with every frame of it inside katachi, so [captureDeclarationSite]
 * would skip past it to whoever started the check. The caller pins the site instead.
 */
internal fun isWrittenByKatachi(block: Any): Boolean = isKatachiFrame(block.javaClass.name)

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
