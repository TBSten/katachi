package me.tbsten.katachi.intellij.ide.authoring.livetemplate

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TemplateContext
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateOptionalProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import me.tbsten.katachi.intellij.ide.sdkCall
import org.jetbrains.kotlin.psi.KtFile

/**
 * Imports the extension functions a katachi live template calls (`assert`, `"…".konsist { }`).
 *
 * toShortenFQNames only shortens a qualified name, and a call on a receiver cannot be written qualified, so these imports
 * are added here instead, after the template is expanded.
 */
internal class KatachiLiveTemplateImports : TemplateOptionalProcessor {

    override fun processText(project: Project, template: Template, document: Document, templateRange: RangeMarker, editor: Editor) {
        val imports = importsOf(template)
        if (imports.isEmpty()) return
        sdkCall("add the imports of the katachi live template ${template.key}") {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitDocument(document)
            val file = documents.getPsiFile(document) as? KtFile ?: return@sdkCall
            val present = file.importDirectives.mapNotNull { it.importedFqName?.asString() }.toSet()
            val missing = imports.filterNot { it in present }
            if (missing.isEmpty()) return@sdkCall
            val lines = missing.joinToString("") { "\nimport $it" }
            val lastImport = file.importDirectives.lastOrNull()
            val packageDirective = file.packageDirective?.takeIf { it.textLength > 0 }
            when {
                lastImport != null -> document.insertString(lastImport.textRange.endOffset, lines)
                packageDirective != null -> document.insertString(packageDirective.textRange.endOffset, "\n$lines")
                else -> document.insertString(0, lines.removePrefix("\n") + "\n\n")
            }
            documents.commitDocument(document)
        }
    }

    override fun getOptionName(): String = "katachi: import the extension functions"

    override fun isEnabled(template: Template): Boolean = importsOf(template).isNotEmpty()

    override fun isVisible(template: Template, context: TemplateContext): Boolean = false

    private fun importsOf(template: Template): List<String> =
        if ((template as? TemplateImpl)?.groupName == GROUP) EXTENSION_IMPORTS[template.key].orEmpty() else emptyList()

    private companion object {
        const val GROUP: String = "katachi"
        val EXTENSION_IMPORTS: Map<String, List<String>> = mapOf(
            "katachitest" to listOf("me.tbsten.katachi.check.assert"),
            "katachikonsist" to listOf("me.tbsten.katachi.konsist.konsist"),
        )
    }
}
