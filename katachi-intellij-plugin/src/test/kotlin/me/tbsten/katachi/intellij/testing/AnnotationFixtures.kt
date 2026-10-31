package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.SarifLocation
import me.tbsten.katachi.intellij.model.ViolationModel
import java.nio.file.Path

/**
 * The violations of a check as katachi writes them (`report { sarif = true }`), for the tests of the editor's annotations:
 * the properties are the ones `SarifDocument` writes for each kind (`katachi/src/test/resources/sarif/golden/every-kind.sarif`),
 * and the message of a declaration is the one katachi-konsist writes for `ktFileContent { }`
 * (`katachi-konsist/src/test/resources/sarif/ktfilecontent.sarif`, whole in `contract/json/katachi-ktfilecontent-sarif.json`).
 * A missing declaration is [missing]: it has a `declaration` (the rule's name) but no line, and its message holds a `Missing:` line.
 *
 * ```kotlin
 * val rejected = AnnotationFixtures.unsatisfied(path, "HealthService", line = 7)
 * ```
 */
internal object AnnotationFixtures {
    val MODULE: ModuleId = module(":arch").id

    /** The name of the `ktFileContent { }` of sample/jvm's `ServiceRole.kt`, as the check writes it in `constraint`. */
    const val CONSTRAINT: String = "Must be public and import models and Repositories only, never HTTP (ktFileContent)"

    /** Where the check says [CONSTRAINT] was written (the fakes' own line, not the sample's). */
    const val RULE_AT: String = "ServiceRole.kt:94"

    /** `UnsatisfiedFileConstraint`: [declaration] was rejected at [line] of [path]; with neither, a violation of the whole file. */
    fun unsatisfied(
        path: Path,
        declaration: String? = null,
        line: Int? = null,
        constraint: String = CONSTRAINT,
        role: String = "domain.Service",
        level: SarifLevel = SarifLevel.Error,
        declaredAt: String = RULE_AT,
        related: List<SarifLocation> = emptyList(),
        message: String = "Role: $role / FileConstraint: \"$constraint\"" +
            (declaration?.let { "\nDeclaration: $it" + (line?.let { n -> " (line $n)" } ?: "") + "\nNot allowed: $it" }.orEmpty()) +
            "\nDeclared at: $declaredAt",
    ): ViolationModel = ViolationModel(
        module = MODULE,
        ruleId = "katachi.UnsatisfiedFileConstraint",
        label = "UnsatisfiedFileConstraint",
        level = level,
        message = message,
        location = SarifLocation(path, line),
        related = related,
        properties = buildMap {
            put("kind", "FileConstraint")
            put("severity", "Error")
            put("path", path.toString())
            put("role", role)
            put("constraint", constraint)
            declaration?.let { put("declaration", it) }
            put("declaredAt", declaredAt)
        },
    )

    /**
     * `UnsatisfiedFileConstraint` for a declaration [path] lacks, as `ktFileContent { }` reports it: the rule's name is the
     * `declaration`, there is no line, and the message says `Missing: <what the rule requires>`. With [found], the file has
     * the declaration in another shape and the message adds a `Found:` line (it is still reported without a line).
     */
    fun missing(
        path: Path,
        declaration: String = "DefaultGetUserUseCase",
        requires: String = "class $declaration : GetUserUseCase",
        found: String? = null,
        constraint: String = "ktFileContent",
        role: String = "domain.UseCase",
        level: SarifLevel = SarifLevel.Error,
        declaredAt: String = "UseCaseRole.kt:20",
        related: List<SarifLocation> = emptyList(),
    ): ViolationModel = ViolationModel(
        module = MODULE,
        ruleId = "katachi.UnsatisfiedFileConstraint",
        label = "UnsatisfiedFileConstraint",
        level = level,
        message = "Role: $role / FileConstraint: \"$constraint\"\nDeclaration: $declaration\nMissing: $requires" +
            (found?.let { "\nFound: $it" }.orEmpty()) +
            "\nDeclared at: $declaredAt\n\nHow to fix:\n  - Declare it in this file\n  - If some files do not need it, write allow(...) in place of required(...) at $declaredAt",
        location = SarifLocation(path, null),
        related = related,
        properties = mapOf(
            "kind" to "FileConstraint", "severity" to "Error", "path" to path.toString(),
            "role" to role, "constraint" to constraint, "declaration" to declaration, "declaredAt" to declaredAt,
        ),
    )

    /** `UncheckedFile`: katachi failed while checking [path]; it has no line. */
    fun unchecked(path: Path, level: SarifLevel = SarifLevel.Error): ViolationModel = ViolationModel(
        module = MODULE,
        ruleId = "katachi.UncheckedFile",
        label = "UncheckedFile",
        level = level,
        message = "Katachi failed while checking this file, so nothing is known about it.\nCause: java.lang.IllegalStateException: unreadable",
        location = SarifLocation(path, null),
        related = emptyList(),
        properties = mapOf("kind" to "Failed", "severity" to "Error", "path" to path.toString()),
    )

    /** `UnexpectedFile`: no role holds [path]. The notification's, not an annotation. */
    fun unexpected(path: Path): ViolationModel = ViolationModel(
        module = MODULE,
        ruleId = "katachi.UnexpectedFile",
        label = "UnexpectedFile",
        level = SarifLevel.Error,
        message = "No role is defined for this file.",
        location = SarifLocation(path, null),
        related = emptyList(),
        properties = mapOf("kind" to "UnexpectedFile", "severity" to "Error", "path" to path.toString()),
    )

    /** `MissingFile`: a role's layout lists a file that is not there; reported on the definition's line. The definition side's. */
    fun missingFile(path: Path, line: Int = 16): ViolationModel = ViolationModel(
        module = MODULE,
        ruleId = "katachi.MissingFile",
        label = "MissingFile",
        level = SarifLevel.Error,
        message = "The file is missing.",
        location = SarifLocation(path, line),
        related = emptyList(),
        properties = mapOf("kind" to "MissingFile", "severity" to "Error", "path" to path.toString(), "role" to "domain.UseCase", "declaredAt" to "UseCaseRole.kt:16"),
    )
}
