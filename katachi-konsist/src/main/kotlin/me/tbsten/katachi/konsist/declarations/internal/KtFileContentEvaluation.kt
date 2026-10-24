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

/*
 * Running the block. It is run once per file, with that file's own `fileName` and
 * `captureValue(...)`, and a few times more with stand-in values: when the
 * definition is read, and when the check starts, to find a mistake before any file is read.
 */

/** The stand-in `fileName` of a run that is about no file in particular. */
internal const val PLACEHOLDER_FILE_NAME: String = "Sample"

/** The stand-in value of `captureValue(name)`: `<name>`, which reads as a placeholder on a role page too. */
internal fun placeholderCaptureValue(name: String): String = "<$name>"

/** What one run of the block is told. */
internal class EvaluationInput(
    fileName: String,
    captureValue: (String) -> String,
    val patterns: PatternCache,
) {
    private val name = fileName
    private val capture = captureValue

    /** Whether the block read [fileName] or [captureValue] during the run. */
    @Volatile
    var readValues: Boolean = false
        private set

    /** The file's name before its first `.`: `Platform.android.kt` is `Platform`. */
    val fileName: String
        get() {
            readValues = true
            return name
        }

    /** The value of `captureValue(name)`, or the exception that says why there is none. */
    val captureValue: (String) -> String = { captured ->
        readValues = true
        capture(captured)
    }

    override fun toString(): String = "EvaluationInput($name)"
}

/**
 * Runs [block] once in a fresh scope and freezes what it wrote.
 *
 * @param declaredAt where `ktFileContent { }` was called.
 */
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

/** The name `fileName` reads for [path]: its last level, up to the first `.`. */
internal fun fileNameOf(path: String): String = path.substringAfterLast('/').substringBefore('.')

/**
 * Runs [block] once with stand-in values while the definition is read, so that a line no
 * declaration can match fails at the line rather than in every file.
 */
internal fun validateAtDeclaration(block: KtFileContentScope.() -> Unit, declaredAt: DeclarationSite, patterns: PatternCache) {
    evaluateWithStandIns(block, EvaluationInput(PLACEHOLDER_FILE_NAME, ::placeholderCaptureValue, patterns), declaredAt)
}

/**
 * Runs [block] once with the stand-ins of [input], to find a mistake in the definition early.
 *
 * Only katachi's own definition errors are let through. Anything else the block throws -- most
 * likely because a stand-in is not a value it expected, such as `fileName.substring(10)` on
 * `Sample` -- is dropped: the run with each file's real values happens anyway, and
 * reports it there. So is a pattern that cannot be read, or a value never passed to `allow(...)`
 * ([KtFileContentLineProblem.DanglingModifiers]), once the block has read a stand-in:
 * `fileName.dropLast(9)` is empty for `Sample` only, and `if (fileName.endsWith("ViewModel"))`
 * is false for it.
 */
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

/**
 * The stand-ins the check starts with, before any file is read: `captureValue` already refuses a
 * name the role's layout does not capture.
 */
internal fun placeholderInput(subject: FileConstraintSubject, declaredAt: DeclarationSite, patterns: PatternCache): EvaluationInput =
    EvaluationInput(
        fileName = PLACEHOLDER_FILE_NAME,
        captureValue = { name ->
            if (name !in subject.captureNames) throw unknownCapture(subject, name, declaredAt)
            placeholderCaptureValue(name)
        },
        patterns = patterns,
    )

/** What the run for [file], one of the subject's files, is told: that file's own name and captured values. */
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
