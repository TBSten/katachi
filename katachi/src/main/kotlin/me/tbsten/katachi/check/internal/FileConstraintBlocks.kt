package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.internal.displayPath

internal fun unsatisfiedFileConstraintBlock(violation: UnsatisfiedFileConstraint, root: String?): List<String> = buildList {
    add("[${violation.label}] ${constraintPath(root, violation.path, violation.declaredAt)}")
    add(
        "$STEP${constraintLine(violation.role.qualifiedName, violation.constraintName)}",
    )
    declarationLine(violation.declaration, violation.line)?.let { add("$STEP$it") }
    for (reason in violation.reasons) add("$STEP${oneLine(reason.label)}: ${oneLine(reason.text)}")
    violation.allowed?.let { allowed ->
        add("$STEP${oneLine(allowed.heading)}:")
        if (allowed.entries.isEmpty()) add("$STEP$STEP(nothing)")
        for (entry in allowed.entries) add("$STEP$STEP${oneLine(entry)}")
    }
    add("${STEP}Declared at: ${violation.reportedDeclaredAt}${inLayout(root, violation.layoutPath)}")
    if (violation.howToFix.isNotEmpty()) {
        add("")
        add("${STEP}How to fix:")
        for (fix in violation.howToFix) {
            add("$STEP$STEP- ${oneLine(fix.text)}")
            addAll(codeLines(fix.code))
        }
    }
}

internal val UnsatisfiedFileConstraint.reportedDeclaredAt: DeclarationSite
    get() = ruleDeclaredAt ?: declaredAt

private fun codeLines(code: List<String>): List<String> =
    code.flatMap { it.lines() }
        .map { it.trimEnd() }
        .map { if (it.isEmpty()) it else "$STEP$STEP$STEP$STEP$it" }

internal fun uncheckedFileConstraintBlock(violation: UncheckedFileConstraint, root: String?): List<String> = buildList {
    add("[${violation.label}] ${constraintPath(root, violation.path, violation.declaredAt)}")
    when (violation.reason) {
        UncheckedFileConstraintReason.NotEvaluated ->
            add("${STEP}Nothing evaluated this constraint, so nothing is known about it.")

        UncheckedFileConstraintReason.Failed ->
            add("${STEP}Katachi failed while evaluating this constraint, so nothing is known about it.")
    }
    add("$STEP${constraintLine(violation.role.qualifiedName, violation.constraintName)}")
    add("${STEP}Declared at: ${violation.declaredAt}${inLayout(root, violation.layoutPath)}")
    violation.cause?.let { addAll(causeLines(it)) }
    add("")
    add("${STEP}How to fix:")
    when (violation.reason) {
        UncheckedFileConstraintReason.NotEvaluated -> {
            add("$STEP$STEP- Pass FileConstraintCheck() to assert(): projectArchitecture.assert(FileConstraintCheck())")
            add("$STEP$STEP- Remove the constraint at ${violation.declaredAt} if it is no longer wanted")
        }

        UncheckedFileConstraintReason.Failed -> {
            add("$STEP$STEP- Read the cause above: it says what stopped the constraint")
            add("$STEP$STEP- Check the constraint block at ${violation.declaredAt}")
            add(
                "$STEP$STEP- If the cause is a KatachiInternalException, report it at " +
                    "https://github.com/TBSten/katachi/issues",
            )
        }
    }
}

private fun constraintLine(role: String, constraintName: String?): String =
    if (constraintName == null) {
        "Role: $role"
    } else {
        "Role: $role / FileConstraint: \"${oneLine(constraintName)}\""
    }

private fun declarationLine(declaration: String?, line: Int?): String? = when {
    declaration == null -> null
    line == null -> "Declaration: ${oneLine(declaration)}"
    else -> "Declaration: ${oneLine(declaration)} (line $line)"
}

// Prints the resolved directory, never the key as written: `":feature:*"` would give every module block the same context.
private fun inLayout(root: String?, layoutPath: String?): String =
    if (layoutPath == null) "" else " (layout of ${displayPath(root, layoutPath)})"

private fun constraintPath(root: String?, path: String, declaredAt: DeclarationSite): String =
    if (path == declaredAt.fileName) path else displayPath(root, path)
