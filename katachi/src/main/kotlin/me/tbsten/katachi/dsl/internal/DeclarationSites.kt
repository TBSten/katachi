package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite

private const val KATACHI_PACKAGE_PREFIX: String = "me.tbsten.katachi."

// Nested inside KATACHI_PACKAGE_PREFIX; katachi's own specs are user code to the DSL, so their frames must survive
// the filter.
private const val KATACHI_TEST_PACKAGE_PREFIX: String = "me.tbsten.katachi.test."

// Skipping all of `me.tbsten.katachi.` would also skip katachi's own specs and land the site inside the test
// runner.
internal fun isKatachiFrame(className: String): Boolean =
    className.startsWith(KATACHI_PACKAGE_PREFIX) &&
        !className.startsWith(KATACHI_TEST_PACKAGE_PREFIX)

// Such blocks have every frame inside katachi, so captureDeclarationSite would skip to whoever started the check;
// the caller pins the site instead.
internal fun isWrittenByKatachi(block: Any): Boolean = isKatachiFrame(block.javaClass.name)

// Lazy walk: cost scales with katachi frames on top, not caller stack depth (kotest and Gradle add hundreds).
private val stackWalker: StackWalker = StackWalker.getInstance()

// Must be reached through a non-inline function: inlined into user code, the frame's line number is remapped past
// the end of the file.
internal fun captureDeclarationSite(): DeclarationSite {
    // TODO: reports print every other path as a `file:///...` URI, but a site stays
    //  `FileName.kt:42`: a frame carries no directory. Resolving `frame.className` to a source
    //  file (package -> directory, per source set) would work for most projects, but Kotlin
    //  does not require the package to match the directory and KMP has several source sets
    //  with same-named files, so a guess could print a wrong absolute path.
    val frame = stackWalker.walk { frames ->
        frames.filter { !isKatachiFrame(it.className) }.findFirst().orElse(null)
    } ?: return DeclarationSite.Unknown
    return DeclarationSite(
        fileName = frame.fileName ?: DeclarationSite.Unknown.fileName,
        lineNumber = frame.lineNumber,
    )
}
