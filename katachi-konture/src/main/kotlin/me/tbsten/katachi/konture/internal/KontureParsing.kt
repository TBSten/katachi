// The one file of this module that reaches Konture's `internal` declarations. Konture 1.0.1
// exposes no public way to turn a source file into a `FileDeclaration`; the graph around it
// (`Module`, `ProjectGraph`, `FileDeclaration`) is public and is assembled in `KontureGraphs.kt`.
// `KontureAssumptionsSpec` pins the four members used here against 1.0.1, and (23) the per-file union `tablesOf` relies on.
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
        fun of(files: List<File>): KontureSymbols = tablesOf(production = files, test = emptyList()).production

        // Each file is read once: the table of every file is the production table plus the test one, which is what
        // reading them all in one go returns, as both getters add file by file (`addAll`, `putAll`). On a type alias
        // declared under the same name on both sides the test one wins, which is the one a test file sees.
        fun tablesOf(production: List<File>, test: List<File>): KontureSymbolTables {
            val classes = PsiParser.getDeclaredClassFqNames(production)
            val typeAliases = PsiParser.getDeclaredTypeAliases(production)
            val productionSymbols = KontureSymbols(MapSymbolLookup(classes, typeAliases))
            if (test.isEmpty()) return KontureSymbolTables(all = productionSymbols, production = productionSymbols)
            val all = KontureSymbols(
                MapSymbolLookup(
                    declaredClasses = classes + PsiParser.getDeclaredClassFqNames(test),
                    typeAliases = typeAliases + PsiParser.getDeclaredTypeAliases(test),
                ),
            )
            return KontureSymbolTables(all = all, production = productionSymbols)
        }
    }
}

// What a test file reads ([all]: it sees the production classes too) and what a production file reads ([production]).
internal class KontureSymbolTables(val all: KontureSymbols, val production: KontureSymbols)
