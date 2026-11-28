package me.tbsten.katachi.script.internal

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

// The one place that reads Kotlin source for `lint` (KATACHI-486). The evaluated definition JSON keeps
// only the line a constraint was called on, so a constraint written inside a Role's braces cannot be told
// from one split out into `private fun RoleScope.x() = ktFileContent { }` there. The source can: this parses
// (PSI only, nothing is resolved or compiled) and walks the braces of each `"Name".role { }`.

/** A constraint call written directly inside a Role: its callee and the line it is on (1-based). */
internal class InlineConstraint(val call: String, val line: Int)

/** A `"Name".role { }` call: the name when it is a plain string, the line of the call, and the constraints inside its braces. */
internal class RoleCall(val name: String?, val line: Int, val inlineConstraints: List<InlineConstraint>)

/** Constraint calls `lint` points at. Anything else (a helper of your own that takes a lambda) is not guessed at. */
private val CONSTRAINT_CALLS = setOf("fileConstraint", "konsist", "ktFileContent", "konture")

/** Parses [text] as [fileName] and returns every Role call in it, in the order they appear. */
internal fun parseRoleCalls(fileName: String, text: String): List<RoleCall> = withPsiFactory { factory ->
    val file = factory.createFile(fileName, text)
    file.collectDescendantsOfType<KtCallExpression>()
        .filter { it.isRoleCall() }
        .map { call -> RoleCall(roleName(call), file.lineOf(call), inlineConstraintsOf(call, file)) }
}

private fun KtCallExpression.isRoleCall(): Boolean {
    if (calleeExpression?.text != "role" || lambdaArguments.isEmpty()) return false
    val receiver = (parent as? KtDotQualifiedExpression)?.receiverExpression
    // "Name".role { } — a template or a name held in a variable is a Role too, but its line is what matches it.
    return receiver != null
}

private fun roleName(call: KtCallExpression): String? {
    val receiver = (call.parent as? KtDotQualifiedExpression)?.receiverExpression as? KtStringTemplateExpression ?: return null
    return receiver.entries.singleOrNull()?.text?.takeIf { !receiver.text.contains('$') }
}

/**
 * Constraint calls whose nearest enclosing Role is [role] and that are not inside a `layout { }` or in the
 * braces of another constraint (`konsist { "x".konsist { } }` is one constraint, the outer one).
 */
private fun inlineConstraintsOf(role: KtCallExpression, file: KtFile): List<InlineConstraint> =
    role.collectDescendantsOfType<KtCallExpression>().filter { call ->
        call !== role && call.calleeExpression?.text in CONSTRAINT_CALLS &&
            (call.lambdaArguments.isNotEmpty() || call.valueArguments.isNotEmpty()) &&
            call.ancestorsUntil(role).none { it.isRoleCall() || it.isCall("layout") || it.isConstraintCall() }
    }.map { InlineConstraint(it.calleeExpression!!.text, file.lineOf(it)) }
        .sortedBy { it.line }

private fun KtCallExpression.isCall(name: String) = calleeExpression?.text == name && lambdaArguments.isNotEmpty()

private fun KtCallExpression.isConstraintCall() =
    calleeExpression?.text in CONSTRAINT_CALLS && (lambdaArguments.isNotEmpty() || valueArguments.isNotEmpty())

private fun PsiElement.ancestorsUntil(stop: PsiElement): List<KtCallExpression> {
    val found = mutableListOf<KtCallExpression>()
    var e: PsiElement? = parent
    while (e != null && e !== stop) {
        if (e is KtCallExpression) found += e
        e = e.parent
    }
    return found
}

private fun KtFile.lineOf(element: PsiElement): Int = text.substring(0, element.textOffset).count { it == '\n' } + 1

@OptIn(CompilerConfiguration.Internals::class, K1Deprecation::class)
private fun <T> withPsiFactory(block: (KtPsiFactory) -> T): T {
    val disposable = Disposer.newDisposable("katachi-lint-psi")
    try {
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "katachi-lint")
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        }
        val environment = KotlinCoreEnvironment.createForProduction(disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        return block(KtPsiFactory(environment.project, markGenerated = false))
    } finally {
        Disposer.dispose(disposable)
    }
}
