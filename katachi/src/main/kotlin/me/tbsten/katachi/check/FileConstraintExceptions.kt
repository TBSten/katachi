package me.tbsten.katachi.check

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.internal.displayPath

private const val LISTED_PATHS: Int = 3

/**
 * A constraint answered about files it was never asked about.
 *
 * katachi refuses the whole answer rather than dropping those rejections, which would make the
 * rule look satisfied, and reports the constraint as an [UncheckedFileConstraint].
 *
 * ## Example 1: catch a backend that answered about the wrong files
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiFileConstraintSubjectException>()
 *     .outside shouldBe listOf("build.gradle.kts")
 * ```
 */
public class KatachiFileConstraintSubjectException internal constructor(
    /** The qualified name of the role whose layout the constraint was written in. */
    public val role: String,
    /** What the constraint was called, or `null` when it was declared without a name. */
    public val constraintName: String?,
    /** Where the constraint was written. */
    public val declaredAt: DeclarationSite,
    /** The paths the constraint answered about that were never handed to it. */
    public val outside: List<String>,
    projectRoot: FsPath? = null,
) : KatachiDeclarationException(
    message = buildString {
        val named = constraintName?.let { "\"$it\"" } ?: "declared at $declaredAt"
        appendLine(
            "File constraint $named of role \"$role\" answered about ${outside.size} " +
                "${if (outside.size == 1) "file" else "files"} it was not asked about: " +
                outside.take(LISTED_PATHS).joinToString(", ") { displayPath(projectRoot?.value, it) } +
                if (outside.size > LISTED_PATHS) ", ..." else "",
        )
        appendLine(
            "A constraint may only reject files it was handed, because a report block opens " +
                "with a path the walk actually saw. Answering about anything else would point " +
                "a reader at a file this constraint does not cover.",
        )
        append(
            "Check the backend at $declaredAt: it is most likely reading a wider set of files " +
                "than `FileConstraintSubject.files`.",
        )
    },
)

/**
 * `FileConstraintCheck(parallelism = ...)` was given fewer than one thread.
 *
 * It is not treated as one, so a miscalculated value (`availableProcessors() - 1` on a
 * one-core CI runner) does not go unnoticed.
 *
 * ## Example 1: catch a parallelism that was worked out to zero
 * ```kt
 * shouldThrow<KatachiInvalidFileConstraintParallelismException> {
 *     FileConstraintCheck(parallelism = 0)
 * }.parallelism shouldBe 0
 * ```
 */
public class KatachiInvalidFileConstraintParallelismException internal constructor(
    /** The value that was passed. */
    public val parallelism: Int,
    /** Where the check was created. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = """
        FileConstraintCheck was created with parallelism = $parallelism at $declaredAt.
        It is the number of threads the constraints are evaluated on, so it has to be 1 or more.
        Pass 1 (the default) to evaluate them one at a time, or a positive thread count such as `Runtime.getRuntime().availableProcessors()`.
    """.trimIndent(),
)
