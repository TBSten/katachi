package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

/**
 * A file that matches a role's pattern except that the levels tied together by one capture name
 * do not hold the same value.
 *
 * For example `feature/home/.../feature/settings/HomeUserCard.kt` against a path that names both
 * `feature` levels alike, or a derived level (`${capture("feature").pascalCase}Screen`) that does
 * not read the conversion of its plain capture.
 *
 * Reported in place of [UnexpectedFile] when no other role allows the file either: the file
 * does have a pattern that nearly fits, and the block says which levels disagree and where the
 * file would have to be for them to agree.
 *
 * ## Example 1: list the files whose captured levels disagree
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filterIsInstance<CaptureMismatch>().map { it.path to it.expectedPath }
 * ```
 */
public class CaptureMismatch internal constructor(
    override val path: String,
    /** The role whose pattern the file matched. */
    public val role: Role,
    /** Where the declaration that tied the levels together was written. */
    public val declaredAt: DeclarationSite,
    /**
     * The declared path with each plain capture written as `${name}`, each derived one as
     * `<name.transform>`, and only a `*` nobody named left as `*`.
     */
    public val pattern: String,
    /** Every occurrence of a bound name on the file's path, in declaration order. */
    public val occurrences: List<CaptureOccurrence>,
    /**
     * The path the file would have if every bound level read the value its name is held to, or
     * `null` when that value could not be worked out.
     */
    public val expectedPath: String?,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Unexpected
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "CaptureMismatch"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}

/**
 * One occurrence of a bound capture name on a file's path, as a [CaptureMismatch] lists it.
 *
 * ## Example 1: read which level disagreed
 * ```kt
 * val mismatch = failure.violations.filterIsInstance<CaptureMismatch>().first()
 * mismatch.occurrences.filter { it.expected != null && it.expected != it.value }.map { it.level }
 * ```
 */
public class CaptureOccurrence internal constructor(
    /** The capture's name; for a derived occurrence, the plain capture's name it derives from. */
    public val name: String,
    /** The naming conversion of a derived occurrence (`"pascalCase"`), `null` for a plain one. */
    public val transform: String?,
    /** The 0-based level of the file's path the occurrence sits on. */
    public val level: Int,
    /** What the file holds at that level. */
    public val value: String,
    /**
     * What the level was held to, or `null` for the first plain occurrence itself.
     *
     * That is the first plain occurrence's value, converted for a derived occurrence.
     */
    public val expected: String?,
) {
    /** `<name>[.<transform>] = <value>`, for a log line or a test failure message. */
    override fun toString(): String = "$name${transform?.let { ".$it" }.orEmpty()} = $value"
}
