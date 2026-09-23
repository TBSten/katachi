package me.tbsten.katachi.test.check

import me.tbsten.katachi.dsl.ConstraintFailure
import me.tbsten.katachi.dsl.ConstraintSubject
import me.tbsten.katachi.dsl.FileSetConstraint
import me.tbsten.katachi.scan.UncheckedConstraint
import me.tbsten.katachi.scan.UnsatisfiedConstraint
import me.tbsten.katachi.scan.Violation

/**
 * The constraint blocks the step 5 specs hand to `KonsistCheck`.
 *
 * All of them are plain [FileSetConstraint]s written inline: what the check does with a
 * constraint has to hold for one written by hand, so nothing here knows what Konsist is.
 *
 * Written as functions rather than values so that each call is its own instance — a spec
 * declaring two constraints must not have them share one, because the evaluated set is keyed
 * by identity.
 */

/** Answers nothing, for a spec that only cares whether the block was reached at all. */
internal fun silentCheck(): FileSetConstraint = FileSetConstraint { emptyList() }

/** Rejects every file it is handed, so what comes back is exactly what it covered. */
internal fun rejectsEverything(): FileSetConstraint =
    FileSetConstraint { subject -> subject.files.map { ConstraintFailure(it) } }

/** Rejects the named files, and only those, when they are part of the subject. */
internal fun rejecting(vararg files: String): FileSetConstraint = FileSetConstraint { subject ->
    subject.files.filter { it in files }.map { ConstraintFailure(it) }
}

/** Throws instead of answering, which is how a rule that is broken rather than unmet looks. */
internal fun throwing(failure: () -> Throwable): FileSetConstraint =
    FileSetConstraint { throw failure() }

/** Records every subject it was handed, so a spec can say how often it ran and over what. */
internal class RecordingConstraint(
    private val answer: (ConstraintSubject) -> List<ConstraintFailure> = { emptyList() },
) : FileSetConstraint {
    val subjects: MutableList<ConstraintSubject> = mutableListOf()

    override fun evaluate(subject: ConstraintSubject): List<ConstraintFailure> {
        subjects += subject
        return answer(subject)
    }
}

/** A value with a type of its own, for the specs about the scratch map's keys. */
internal class ScratchValue(val label: String)

/** The constraints nothing could answer for, which is what the guard produces. */
internal fun List<Violation>.unchecked(): List<UncheckedConstraint> =
    filterIsInstance<UncheckedConstraint>()

/** The constraints that said no, which is what a satisfied run has none of. */
internal fun List<Violation>.unsatisfied(): List<UnsatisfiedConstraint> =
    filterIsInstance<UnsatisfiedConstraint>()
