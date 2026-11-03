package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.gutter.SourceDeclarations
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

// Reads the PSI's declarations only (no index) so it works in dumb mode. Call under a read lock.
internal object AnnotationRanges {
    // A declaration of the last name starting exactly at `nearLine` wins: the `konsist { }` backend names a member by its bare name,
    // so a top-level `foo` must not take the underline meant for `Bar.foo`.
    // TODO(ide-gutter): a `konsist { }` rejection of the file itself (`KoFileDeclaration`) has the file's name and no line, and lands on the class of that name; katachi would have to say "the target is the file" in `properties` (background F-13).
    fun declarationRange(file: PsiFile, document: Document, name: String, nearLine: Int?): TextRange? {
        val ktFile = file as? KtFile ?: return null
        val path = name.split('.').filter { it.isNotEmpty() }
        if (path.isEmpty()) return null
        if (nearLine != null) {
            val onTheLine = allNamedDeclarations(ktFile.declarations.filterIsInstance<KtNamedDeclaration>())
                .firstOrNull { it.name == path.last() && it.nameIdentifier != null && distance(document, it, nearLine) == 0 }
            if (onTheLine != null) return onTheLine.nameIdentifier?.textRange
        }
        var candidates: List<KtNamedDeclaration> = ktFile.declarations.filterIsInstance<KtNamedDeclaration>().filter { it.name == path.first() }
        for (member in path.drop(1)) candidates = candidates.flatMap { membersOf(it) }.filter { it.name == member }
        val chosen = candidates.filter { it.nameIdentifier != null }.minWithOrNull(
            compareBy<KtNamedDeclaration> { distance(document, it, nearLine) }.thenBy { it.textRange.startOffset },
        )
        return chosen?.nameIdentifier?.textRange
    }

    fun lineRange(document: Document, line: Int): TextRange? {
        if (line < 1 || line > document.lineCount) return null
        val start = document.getLineStartOffset(line - 1)
        val end = document.getLineEndOffset(line - 1)
        val text = document.charsSequence
        var first = start
        while (first < end && text[first].isWhitespace()) first++
        return if (first < end) TextRange(first, end) else null
    }

    private fun allNamedDeclarations(declarations: List<KtNamedDeclaration>): List<KtNamedDeclaration> =
        declarations.flatMap { listOf(it) + allNamedDeclarations(membersOf(it)) }

    private fun membersOf(declaration: KtNamedDeclaration): List<KtNamedDeclaration> {
        val container = declaration as? KtClassOrObject ?: return emptyList()
        // A property written in the primary constructor (`class A(val b: Int)`) is a member too.
        return container.primaryConstructorParameters.filter { it.hasValOrVar() } + container.declarations.filterIsInstance<KtNamedDeclaration>()
    }

    private fun distance(document: Document, declaration: KtNamedDeclaration, nearLine: Int?): Int {
        if (nearLine == null) return 0
        val line = document.getLineNumber(SourceDeclarations.startOffsetOf(declaration).coerceIn(0, document.textLength)) + 1
        return kotlin.math.abs(line - nearLine)
    }
}
