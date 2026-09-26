package me.tbsten.katachi.test.check

import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.FileConstraint

/**
 * The constraint blocks the step 5 specs hand to `FileConstraintCheck`.
 *
 * All of them are plain [FileConstraint]s written inline: what the check does with a
 * constraint has to hold for one written by hand, so nothing here knows what Konsist is.
 *
 * Written as functions rather than values so that each call is its own instance — a spec
 * declaring two constraints must not have them share one, because the evaluated set is keyed
 * by identity.
 */

/** Answers nothing, for a spec that only cares whether the block was reached at all. */
internal fun silentCheck(): FileConstraint = FileConstraint { emptyList() }

/** Rejects every file it is handed, so what comes back is exactly what it covered. */
internal fun rejectsEverything(): FileConstraint =
    FileConstraint { subject -> subject.files.map { FileConstraintFailure(it) } }

/** Rejects the named files, and only those, when they are part of the subject. */
internal fun rejecting(vararg files: String): FileConstraint = FileConstraint { subject ->
    subject.files.filter { it in files }.map { FileConstraintFailure(it) }
}

/** Throws instead of answering, which is how a rule that is broken rather than unmet looks. */
internal fun throwing(failure: () -> Throwable): FileConstraint =
    FileConstraint { throw failure() }

/** Records every subject it was handed, so a spec can say how often it ran and over what. */
internal class RecordingFileConstraint(
    private val answer: (FileConstraintSubject) -> List<FileConstraintFailure> = { emptyList() },
) : FileConstraint {
    val subjects: MutableList<FileConstraintSubject> = mutableListOf()

    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> {
        subjects += subject
        return answer(subject)
    }
}

/** A value with a type of its own, for the specs about the scratch map's keys. */
internal class ScratchValue(val label: String)

/** The constraints nothing could answer for, which is what the guard produces. */
internal fun List<Violation>.unchecked(): List<UncheckedFileConstraint> =
    filterIsInstance<UncheckedFileConstraint>()

/** The constraints that said no, which is what a satisfied run has none of. */
internal fun List<Violation>.unsatisfied(): List<UnsatisfiedFileConstraint> =
    filterIsInstance<UnsatisfiedFileConstraint>()
