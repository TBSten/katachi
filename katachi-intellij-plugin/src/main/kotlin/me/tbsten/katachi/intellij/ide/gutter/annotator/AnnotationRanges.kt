package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.gutter.SourceDeclarations
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/**
 * Where in a file an annotation goes (design 2.5): the name of a declaration, else a line. Reads the PSI's declarations
 * only, no index, so it works in dumb mode.
 *
 * Call under a read lock.
 *
 * ```kotlin
 * val range = AnnotationRanges.declarationRange(ktFile, document, "HealthService", nearLine = 7)
 *     ?: AnnotationRanges.lineRange(document, 7)
 * ```
 */
internal object AnnotationRanges {
    /**
     * The name of the declaration [name] (`Outer.member` for a member: the last name is looked for in `Outer`, and in
     * what its members hold in turn). Of several of one name (overloads), the one that starts nearest [nearLine].
     * `null` when the file has none.
     *
     * A declaration of the last name that starts exactly at [nearLine] wins wherever it sits: the `konsist { }` backend
     * names a member by its bare name (`foo` for `Bar.foo`), and a top-level `foo` must not take its underline when the
     * check saw `foo` on another line. The line is only a hint otherwise (the file may have been edited since the check).
     */
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

    /** From the first character that is not white space of [line] (1-based) to its end; `null` for a line the file does not have or an empty one. */
    fun lineRange(document: Document, line: Int): TextRange? {
        if (line < 1 || line > document.lineCount) return null
        val start = document.getLineStartOffset(line - 1)
        val end = document.getLineEndOffset(line - 1)
        val text = document.charsSequence
        var first = start
        while (first < end && text[first].isWhitespace()) first++
        return if (first < end) TextRange(first, end) else null
    }

    /** [declarations] and, in turn, the members of each at every depth. */
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
