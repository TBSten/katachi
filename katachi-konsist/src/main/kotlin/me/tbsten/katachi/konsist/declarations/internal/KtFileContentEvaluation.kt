package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentCaptureUnavailableException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentUnknownCaptureException
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.KtFileContentScopeImpl

internal const val PLACEHOLDER_FILE_NAME: String = "Sample"

internal fun placeholderCaptureValue(name: String): String = "<$name>"

internal class EvaluationInput(
    fileName: String,
    captureValue: (String) -> String,
    val patterns: PatternCache,
) {
    private val name = fileName
    private val capture = captureValue

    @Volatile
    var readValues: Boolean = false
        private set

    val fileName: String
        get() {
            readValues = true
            return name
        }

    val captureValue: (String) -> String = { captured ->
        readValues = true
        capture(captured)
    }

    override fun toString(): String = "EvaluationInput($name)"
}

internal fun evaluateRules(
    block: KtFileContentScope.() -> Unit,
    input: EvaluationInput,
    declaredAt: DeclarationSite,
): FileRules {
    val session = EvaluationSession(input)
    val file = FileBuilder(session, declaredAt)
    try {
        session.within(file.declarations) { KtFileContentScopeImpl(file).block() }
        session.failOnDanglingValues()
    } finally {
        session.close()
    }
    return file.build()
}

internal fun fileNameOf(path: String): String = path.substringAfterLast('/').substringBefore('.')

internal fun validateAtDeclaration(block: KtFileContentScope.() -> Unit, declaredAt: DeclarationSite, patterns: PatternCache) {
    evaluateWithStandIns(block, EvaluationInput(PLACEHOLDER_FILE_NAME, ::placeholderCaptureValue, patterns), declaredAt)
}

// Only katachi's own definition errors are let through: anything else is likely a stand-in the block did not expect, and the run with real values reports it.
// The same goes for an unreadable pattern or `DanglingModifiers` once the block has read a stand-in (`fileName.dropLast(9)` is empty for `Sample` only).
internal fun evaluateWithStandIns(block: KtFileContentScope.() -> Unit, input: EvaluationInput, declaredAt: DeclarationSite) {
    try {
        evaluateRules(block, input, declaredAt)
    } catch (cause: KatachiKtFileContentPatternException) {
        if (!input.readValues) throw cause
    } catch (cause: KatachiKtFileContentLineException) {
        // A value kept in a variable and used under `if (fileName ...)` is unused for the stand-in only.
        val standInOnly = cause.problem == KtFileContentLineProblem.DanglingModifiers && input.readValues
        if (!standInOnly) throw cause
    } catch (cause: KatachiDeclarationException) {
        throw cause
    } catch (cause: InterruptedException) {
        throw cause
    } catch (ignored: Exception) {
        // The stand-in values, not the definition, are what failed here.
    }
}

internal fun placeholderInput(subject: FileConstraintSubject, declaredAt: DeclarationSite, patterns: PatternCache): EvaluationInput =
    EvaluationInput(
        fileName = PLACEHOLDER_FILE_NAME,
        captureValue = { name ->
            if (name !in subject.captureNames) throw unknownCapture(subject, name, declaredAt)
            placeholderCaptureValue(name)
        },
        patterns = patterns,
    )

internal fun inputFor(
    subject: FileConstraintSubject,
    file: String,
    declaredAt: DeclarationSite,
    patterns: PatternCache,
): EvaluationInput {
    val values by lazy { subject.captureValuesOf(file) }
    return EvaluationInput(
        fileName = fileNameOf(file),
        captureValue = { name ->
            values[name] ?: if (name in subject.captureNames) {
                throw KatachiKtFileContentCaptureUnavailableException(
                    name = name,
                    file = file,
                    role = subject.role.qualifiedName,
                    declaredAt = declaredAt,
                    projectRoot = subject.projectRoot,
                )
            } else {
                throw unknownCapture(subject, name, declaredAt)
            }
        },
        patterns = patterns,
    )
}

private fun unknownCapture(subject: FileConstraintSubject, name: String, declaredAt: DeclarationSite) =
    KatachiKtFileContentUnknownCaptureException(
        name = name,
        role = subject.role.qualifiedName,
        available = subject.captureNames.toList(),
        declaredAt = declaredAt,
    )
