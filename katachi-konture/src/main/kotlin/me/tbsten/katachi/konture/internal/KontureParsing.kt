// The one file of this module that reaches Konture's `internal` declarations. Konture 1.0.1
// exposes no public way to turn a source file into a `FileDeclaration`; the graph around it
// (`Module`, `ProjectGraph`, `FileDeclaration`) is public and is assembled in `KontureGraphs.kt`.
// `KontureAssumptionsSpec` pins the four members used here against 1.0.1.
//
// The suppression is unspecified compiler behaviour, hence `-Xdont-warn-on-error-suppression`
// in build.gradle.kts. The two alternatives, should Kotlin stop honouring it, are
// `-Xfriend-paths=<konture jar>` and reflection; both are measured in
// `.local/v0.3/konture/background.md` D-1.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.tbsten.katachi.konture.internal

import io.github.baole.konture.FileDeclaration
import io.github.baole.konture.impl.PsiParser
import io.github.baole.konture.impl.psi.MapSymbolLookup
import io.github.baole.konture.impl.psi.SymbolLookup
import java.io.File

/**
 * What Konture needs to know about the covered files before parsing any one of them: the
 * classes and type aliases they declare, so that `B` written in one file resolves to
 * `com.example.B` declared in another.
 *
 * Opaque on purpose. Konture's `SymbolLookup` is internal, and keeping its name inside this
 * file is what keeps the suppression above to one file.
 */
internal class KontureSymbols private constructor(private val lookup: SymbolLookup) {
    /**
     * Konture's parse of [file], or `null` when the file does not exist.
     *
     * `FileDeclaration.filePath` is `file.absolutePath` as handed in, not a canonical path, so
     * the caller can compare it with what it asked for.
     */
    fun parse(file: File): FileDeclaration? = PsiParser.parseFile(file, lookup)

    companion object {
        /** The symbols declared across [files], read the way Konture's own loader reads a source set. */
        fun of(files: List<File>): KontureSymbols = KontureSymbols(
            MapSymbolLookup(
                declaredClasses = PsiParser.getDeclaredClassFqNames(files),
                typeAliases = PsiParser.getDeclaredTypeAliases(files),
            ),
        )
    }
}
