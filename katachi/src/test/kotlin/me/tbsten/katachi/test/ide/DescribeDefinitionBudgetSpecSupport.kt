package me.tbsten.katachi.test.ide

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.internal.DeclarationExplaining
import me.tbsten.katachi.dsl.internal.DeclarationExplanation
import me.tbsten.katachi.dsl.internal.DeclarationVerdict
import me.tbsten.katachi.dsl.internal.FileDeclarationsExplanation
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.test.dsl.files.fakeFileSystem

/*
 * What the `maxExplainedFiles` budget and the `line` of a declaration are checked with: a constraint
 * that refuses a subject without a `.kt` file the way `ktFileContent { }` does, and one whose
 * declarations Konsist could not place.
 */

/**
 * Explains every `.kt` file of the subject as one allowed class named after it, and throws when the
 * subject holds no `.kt` file at all, as `ktFileContent { }` does (`KatachiKonsistNoKotlinFilesException`).
 */
internal object ExplainRequiringKotlin : FileConstraint, DeclarationExplaining {
    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> = emptyList()

    override fun explainDeclarations(subject: FileConstraintSubject): List<FileDeclarationsExplanation> {
        val kotlinFiles = subject.files.filter { it.endsWith(".kt") }
        check(kotlinFiles.isNotEmpty()) { "no Kotlin file among ${subject.files}" }
        return kotlinFiles.map { file ->
            val name = file.substringAfterLast('/').removeSuffix(".kt")
            FileDeclarationsExplanation(
                file = file,
                declarationsChecked = true,
                declarations = listOf(DeclarationExplanation("class", name, 3, DeclarationVerdict.Unexpected, emptyList(), null)),
                missing = emptyList(),
                unexplainedReason = null,
            )
        }
    }
}

/** Explains one file with two declarations: one at line 3 and one Konsist could not place (`line = null`). */
internal object ExplainWithUnplacedDeclaration : FileConstraint, DeclarationExplaining {
    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> = emptyList()

    override fun explainDeclarations(subject: FileConstraintSubject): List<FileDeclarationsExplanation> =
        subject.files.map { file ->
            FileDeclarationsExplanation(
                file = file,
                declarationsChecked = true,
                declarations = listOf(
                    DeclarationExplanation("class", "Placed", 3, DeclarationVerdict.Unexpected, emptyList(), null),
                    DeclarationExplanation("fun", "Lost", null, DeclarationVerdict.Unexpected, emptyList(), null),
                ),
                missing = emptyList(),
                unexplainedReason = null,
            )
        }
}

/**
 * `budget`: a block whose `.md` files sort before its `.kt` files, so that a cut of the covered
 * files by count would leave the `.md` ones alone.
 */
internal fun budgetDefinition(): Architecture = architecture {
    files = wholeTree()
    "Docs".role {
        layout {
            "docs" {
                "*.md".file()
                "*.kt".file()
                fileConstraint("needs a Kotlin file", check = ExplainRequiringKotlin)
            }
        }
    }
}

/** The project [budgetDefinition] is walked over: two `.md` files and two `.kt` files. */
internal fun budgetProject(): KatachiFileSystem = fakeFileSystem(workingDirectory = "/repo") {
    "/repo" {
        "gradlew"()
        "docs" {
            "a.md"()
            "b.md"()
            "c.kt"()
            "d.kt"()
        }
    }
}

/** `unplaced`: one role whose constraint explains a declaration that has no line. */
internal fun unplacedDefinition(): Architecture = architecture {
    files = wholeTree()
    "Service".role {
        layout {
            "src" {
                "*.kt".file()
                fileConstraint("places one", check = ExplainWithUnplacedDeclaration)
            }
        }
    }
}

/** The project [unplacedDefinition] is walked over. */
internal fun unplacedProject(): KatachiFileSystem = fakeFileSystem(workingDirectory = "/repo") {
    "/repo" {
        "gradlew"()
        "src" { "A.kt"() }
    }
}
