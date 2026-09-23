package me.tbsten.katachi.check

import me.tbsten.katachi.scan.UncheckedConstraint
import me.tbsten.katachi.scan.UncheckedConstraintReason
import me.tbsten.katachi.scan.UnsatisfiedConstraint

/**
 * The report blocks of the two constraint violations.
 *
 * Split out of `ViolationReport.kt` on size alone — that file is the one place a reader looks
 * to change how a report reads, and it is already at the top of what this repository asks a
 * file to be. The shared pieces (`STEP`, `oneLine`, `causeLine`) moved up to `internal` in the
 * same move, so there is still exactly one definition of each.
 */

/**
 * A constraint that said no.
 *
 * There is no `How to fix:`. The block a constraint holds is an arbitrary Kotlin expression,
 * so katachi does not know what would satisfy it, and a report that guessed would be inventing
 * a claim. The context lines say enough to find the rule and read it.
 */
internal fun unsatisfiedConstraintBlock(violation: UnsatisfiedConstraint): List<String> = buildList {
    add("[${violation.label}] ${violation.path}")
    add(
        "$STEP${constraintLine(violation.role.qualifiedName, violation.constraintName)}",
    )
    declarationLine(violation.declaration, violation.line)?.let { add("$STEP$it") }
    add("${STEP}Declared at: ${violation.declaredAt}${inLayout(violation.layoutPath)}")
}

/**
 * A constraint nothing could answer for.
 *
 * The two reasons print the same context and differ in one sentence and in whether there is a
 * cause to show. `How to fix:` is three lines for [UncheckedConstraintReason.Failed] and says
 * nothing about whose bug it is: the cause may be katachi's, the backend's, or the rule's, and
 * a line that always blamed katachi would be wrong most of the time.
 */
internal fun uncheckedConstraintBlock(violation: UncheckedConstraint): List<String> = buildList {
    add("[${violation.label}] ${violation.path}")
    when (violation.reason) {
        UncheckedConstraintReason.NotEvaluated ->
            add("${STEP}Nothing evaluated this constraint, so nothing is known about it.")

        UncheckedConstraintReason.Failed ->
            add("${STEP}Katachi failed while evaluating this constraint, so nothing is known about it.")
    }
    add("$STEP${constraintLine(violation.role.qualifiedName, violation.constraintName)}")
    add("${STEP}Declared at: ${violation.declaredAt}${inLayout(violation.layoutPath)}")
    violation.cause?.let { add("${STEP}Cause: ${causeLine(it)}") }
    add("")
    add("${STEP}How to fix:")
    when (violation.reason) {
        UncheckedConstraintReason.NotEvaluated -> {
            add("$STEP$STEP- Pass ConstraintCheck() to assert(): projectArchitecture.assert(ConstraintCheck())")
            add("$STEP$STEP- Remove the constraint at ${violation.declaredAt} if it is no longer wanted")
        }

        UncheckedConstraintReason.Failed -> {
            add("$STEP$STEP- Read the cause above: it says what stopped the constraint")
            add("$STEP$STEP- Check the constraint block at ${violation.declaredAt}")
            add(
                "$STEP$STEP- If the cause is a KatachiInternalException, report it at " +
                    "https://github.com/TBSten/katachi/issues",
            )
        }
    }
}

/**
 * The role and, when it has one, the constraint's name — folded onto one line each, because a
 * name is written by a user and a report block's shape is what an agent greps against.
 */
private fun constraintLine(role: String, constraintName: String?): String =
    if (constraintName == null) {
        "Role: $role"
    } else {
        "Role: $role / Constraint: \"${oneLine(constraintName)}\""
    }

/** What inside the file was rejected, or nothing when the backend works per whole file. */
private fun declarationLine(declaration: String?, line: Int?): String? = when {
    declaration == null -> null
    line == null -> "Declaration: ${oneLine(declaration)}"
    else -> "Declaration: ${oneLine(declaration)} (line $line)"
}

/**
 * Which `layout { }` block the constraint sits in, as a resolved directory.
 *
 * Never the key as written: `":feature:*"` is evaluated once per feature module, so printing
 * the key would give every one of those blocks the same context line and leave the reader
 * unable to tell which module the failure is about.
 */
private fun inLayout(layoutPath: String?): String =
    if (layoutPath == null) "" else " (layout of $layoutPath)"
