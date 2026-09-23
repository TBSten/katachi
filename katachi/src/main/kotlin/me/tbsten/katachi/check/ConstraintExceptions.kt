package me.tbsten.katachi.check

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/** How many of the offending paths a message names before it stops listing them. */
private const val LISTED_PATHS: Int = 3

/**
 * A constraint answered about files it was never asked about.
 *
 * A backend that has quietly stopped seeing the right files has no way of finding out on its
 * own: every rejection it invents about a path outside the subject would be dropped, and the
 * run would look like a rule that holds. So katachi refuses the whole answer instead, and the
 * constraint is reported as one nothing is known about.
 *
 * @property role the qualified name of the role whose layout the constraint was written in.
 * @property constraintName what the constraint was called, or `null`.
 * @property declaredAt where the constraint was written.
 * @property outside the paths that were answered about but never handed over.
 *
 * ## Example 1: catch a backend that answered about the wrong files
 * ```kt
 * projectArchitecture.validate(KonsistCheck())
 *     .filterIsInstance<UncheckedConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiConstraintSubjectException>()
 * ```
 */
public class KatachiConstraintSubjectException internal constructor(
    /**
     * The qualified name of the role whose layout the constraint was written in.
     *
     * ## Example 1: read back which role's constraint misbehaved
     * ```kt
     * cause.shouldBeInstanceOf<KatachiConstraintSubjectException>().role shouldBe "domain/UseCase"
     * ```
     */
    public val role: String,
    /**
     * What the constraint was called, or `null` when it was declared without a name.
     *
     * ## Example 1: name the rule that answered about the wrong files
     * ```kt
     * cause.shouldBeInstanceOf<KatachiConstraintSubjectException>().constraintName shouldBe "Internal only"
     * ```
     */
    public val constraintName: String?,
    /**
     * Where the constraint was written.
     *
     * ## Example 1: jump to the constraint whose backend went off course
     * ```kt
     * cause.shouldBeInstanceOf<KatachiConstraintSubjectException>().declaredAt.fileName shouldBe
     *     "ProjectArchitecture.kt"
     * ```
     */
    public val declaredAt: DeclarationSite,
    /**
     * The paths the constraint answered about that were never handed to it.
     *
     * ## Example 1: read back the paths that were not part of the subject
     * ```kt
     * cause.shouldBeInstanceOf<KatachiConstraintSubjectException>().outside shouldBe
     *     listOf("build.gradle.kts")
     * ```
     */
    public val outside: List<String>,
) : KatachiDeclarationException(
    message = buildString {
        val named = constraintName?.let { "\"$it\"" } ?: "declared at $declaredAt"
        appendLine(
            "Constraint $named of role \"$role\" answered about ${outside.size} " +
                "${if (outside.size == 1) "file" else "files"} it was not asked about: " +
                outside.take(LISTED_PATHS).joinToString(", ") +
                if (outside.size > LISTED_PATHS) ", ..." else "",
        )
        appendLine(
            "A constraint may only reject files it was handed, because a report block opens " +
                "with a path the walk actually saw. Answering about anything else would point " +
                "a reader at a file this constraint does not cover.",
        )
        append(
            "Check the backend at $declaredAt: it is most likely reading a wider set of files " +
                "than `ConstraintSubject.files`.",
        )
    },
)
