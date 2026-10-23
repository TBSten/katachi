package me.tbsten.katachi.konsist.declarations

import java.net.URI
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * `captureValue("...")` names a capture the role's layout does not have, found when the check
 * starts.
 *
 * While the definition is read the layout is not known yet (a `layout { }` is evaluated later),
 * so the block runs with stand-in values first; the names are checked against the layout once the
 * check knows it, before any file is read. Reported as `[UncheckedFileConstraint]`.
 *
 * ## Example 1: catch a misspelled capture name
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> { projectArchitecture.assert(FileConstraintCheck()) }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>().single()
 *     .cause.shouldBeInstanceOf<KatachiKtFileContentUnknownCaptureException>().name shouldBe "featur"
 * ```
 */
@ExperimentalKatachiApi
public class KatachiKtFileContentUnknownCaptureException internal constructor(
    /**
     * The name `captureValue` was asked for.
     *
     * ## Example 1: read back the misspelled name
     * ```kt
     * error.name shouldBe "featur"
     * ```
     */
    public val name: String,
    /**
     * The qualified name of the role the block is written for.
     *
     * ## Example 1: read back the role
     * ```kt
     * error.role shouldBe "ViewModel"
     * ```
     */
    public val role: String,
    /**
     * The names the role's layout does capture, in the order they were written.
     *
     * ## Example 1: list the names that would have worked
     * ```kt
     * error.available shouldBe listOf("feature")
     * ```
     */
    public val available: List<String>,
    /**
     * Where `ktFileContent { }` was called.
     *
     * ## Example 1: jump to the block
     * ```kt
     * error.declaredAt.fileName shouldBe "ViewModelRole.kt"
     * ```
     */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = listOf(
        "captureValue(\"$name\") in ktFileContent { } at $declaredAt names no capture of role $role.",
        if (available.isEmpty()) {
            "The role's layout captures nothing, so captureValue has no name to read."
        } else {
            "The role's layout captures: ${available.joinToString(", ")}."
        },
        "Use one of those names, or add capture(\"$name\") to the layout path.",
    ).joinToString("\n"),
)

/**
 * `captureValue("...")` names a capture of the role, but its value cannot be told for the file
 * being checked.
 *
 * A role whose files are declared by more than one layout path may capture a name on one path
 * only, and a capture next to an unnamed `*` in the same level has no value katachi can read off
 * the path. Reported as `[UncheckedFileConstraint]`.
 *
 * ## Example 1: catch a capture one of the role's paths does not have
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> { projectArchitecture.assert(FileConstraintCheck()) }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>().single()
 *     .cause.shouldBeInstanceOf<KatachiKtFileContentCaptureUnavailableException>().file shouldBe "core/Foo.kt"
 * ```
 */
@ExperimentalKatachiApi
public class KatachiKtFileContentCaptureUnavailableException internal constructor(
    /**
     * The name `captureValue` was asked for.
     *
     * ## Example 1: read back the name
     * ```kt
     * error.name shouldBe "feature"
     * ```
     */
    public val name: String,
    /**
     * The file being checked, project relative.
     *
     * ## Example 1: read back which file had no value
     * ```kt
     * error.file shouldBe "core/Foo.kt"
     * ```
     */
    public val file: String,
    /**
     * The qualified name of the role the block is written for.
     *
     * ## Example 1: read back the role
     * ```kt
     * error.role shouldBe "ViewModel"
     * ```
     */
    public val role: String,
    /**
     * Where `ktFileContent { }` was called.
     *
     * ## Example 1: jump to the block
     * ```kt
     * error.declaredAt.lineNumber
     * ```
     */
    public val declaredAt: DeclarationSite,
    projectRoot: String,
) : KatachiDeclarationException(
    message = listOf(
        "captureValue(\"$name\") has no value for ${fileUriOf(projectRoot, file)} " +
            "(role $role, ktFileContent { } at $declaredAt).",
        "The layout path that matches this file does not capture \"$name\", or captures it next to an unnamed * " +
            "in the same level, where its value cannot be read off the path.",
        "Capture it on every path of the role, or branch on fileName before calling captureValue.",
    ).joinToString("\n"),
)

/**
 * One role with two `ktFileContent { }` blocks, found when the check runs the second one.
 *
 * Each block is an allow list of what a file may hold, so two of them over one file would each
 * reject what the other allows. Reported as `[UncheckedFileConstraint]`.
 *
 * ## Example 1: catch a role that writes the block twice
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> { projectArchitecture.assert(FileConstraintCheck()) }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>().single()
 *     .cause.shouldBeInstanceOf<KatachiKtFileContentDuplicateException>().role shouldBe "ViewModel"
 * ```
 */
@ExperimentalKatachiApi
public class KatachiKtFileContentDuplicateException internal constructor(
    /**
     * The qualified name of the role.
     *
     * ## Example 1: read back the role
     * ```kt
     * error.role shouldBe "ViewModel"
     * ```
     */
    public val role: String,
    /**
     * Where the block that ran first was written.
     *
     * ## Example 1: jump to the first block
     * ```kt
     * error.firstDeclaredAt.lineNumber
     * ```
     */
    public val firstDeclaredAt: DeclarationSite,
    /**
     * Where the second block was written.
     *
     * ## Example 1: jump to the second block
     * ```kt
     * error.declaredAt.lineNumber
     * ```
     */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = listOf(
        "Role $role declares ktFileContent { } twice: at $firstDeclaredAt and at $declaredAt.",
        "Each block is an allow list for the role's files, so two of them would each reject what the other allows.",
        "Merge them into one ktFileContent { }.",
    ).joinToString("\n"),
)

/**
 * [relative] under [projectRoot] as a `file:///...` URI, the form every report prints a path in so
 * that a terminal shows it as a link.
 */
private fun fileUriOf(projectRoot: String, relative: String): String {
    val slashed = "${projectRoot.trimEnd('/')}/$relative".replace('\\', '/')
    val path = if (slashed.startsWith("/")) slashed else "/$slashed"
    return URI("file", "", path, null).toASCIIString()
}
