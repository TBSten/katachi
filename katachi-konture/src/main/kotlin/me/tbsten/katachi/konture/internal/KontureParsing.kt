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

// Opaque on purpose: Konture's `SymbolLookup` is internal, and keeping its name inside this file keeps the suppression to one file.
internal class KontureSymbols private constructor(private val lookup: SymbolLookup) {
    // `FileDeclaration.filePath` is `file.absolutePath` as handed in, not canonical, so the caller can compare it with what it asked for.
    fun parse(file: File): FileDeclaration? = PsiParser.parseFile(file, lookup)

    companion object {
        fun of(files: List<File>): KontureSymbols = KontureSymbols(
            MapSymbolLookup(
                declaredClasses = PsiParser.getDeclaredClassFqNames(files),
                typeAliases = PsiParser.getDeclaredTypeAliases(files),
            ),
        )
    }
}
