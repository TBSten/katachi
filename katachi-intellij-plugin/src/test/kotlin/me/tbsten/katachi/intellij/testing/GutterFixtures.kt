package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.model.ConstraintBackend
import me.tbsten.katachi.intellij.model.DeclarationModel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.EntryMatch
import me.tbsten.katachi.intellij.model.FileDeclarationsModel
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import java.nio.file.Path

/**
 * The gutter's test data: short sources written after sample/jvm (`HealthService.kt`, `HealthRepository.kt`) and
 * builders of what the definition says about them, the roles that hold a file and katachi's explanation of its
 * declarations.
 *
 * ```kotlin
 * val service = roleModel("domain.Service", layoutEntry(GutterFixtures.SERVICE_PATH))
 * val roles = fileRoles(GutterFixtures.SERVICE_PATH, service)
 * val explained = roleFileDeclarations(service, fileDeclarations(GutterFixtures.SERVICE_PATH, allowed(SourceDeclarationKind.Class, "HealthService", 7)))
 * ```
 */
internal object GutterFixtures {
    const val SERVICE_PATH: String = "src/main/kotlin/com/example/service/HealthService.kt"
    const val REPOSITORY_PATH: String = "src/main/kotlin/com/example/repository/HealthRepository.kt"

    /** sample/jvm's `HealthService.kt`: `class HealthService` is on line 7. */
    val HEALTH_SERVICE_KT: String = """
        package com.example.service

        import com.example.model.Health
        import com.example.repository.HealthRepository

        /** Application specific behaviour behind `GET /health`. */
        class HealthService(
            private val healthRepository: HealthRepository = HealthRepository(),
        ) {
            fun currentHealth(): Health = healthRepository.load()
        }
    """.trimIndent()

    /** sample/jvm's `HealthRepository.kt`, whose role writes no `ktFileContent { }`. */
    val HEALTH_REPOSITORY_KT: String = """
        package com.example.repository

        import com.example.model.Health

        class HealthRepository {
            fun load(): Health = Health(status = "ok")
        }
    """.trimIndent()

    /** The line of sample/jvm's `ServiceRole.kt` that allows `class HealthService`: `` `class`(fileName) { ``. */
    val SERVICE_RULE: RuleRef = RuleRef("class HealthService", DeclarationSiteRef("ServiceRole.kt", 80))
}

/** The answer of `forFile` for [relativePath]: [roles] hold it (in that order), or none and [outcome] says why. */
internal fun fileRoles(
    relativePath: String,
    vararg roles: RoleModel,
    outcome: MatchOutcome = if (roles.isEmpty()) MatchOutcome.Unexpected else MatchOutcome.Matched,
    module: KatachiModule = module(":arch"),
    projectRoot: Path = DEFINITION_ROOT,
    freshness: Freshness = Freshness.Fresh,
): FileRoles = FileRoles(
    module = module,
    projectRoot = projectRoot,
    relativePath = relativePath,
    matches = roles.map { role -> EntryMatch(role, role.layout.firstOrNull() ?: layoutEntry(relativePath)) },
    constraints = emptyList(),
    outcome = outcome,
    nearby = emptyList(),
    freshness = freshness,
)

internal fun declarationModel(
    kind: SourceDeclarationKind,
    name: String,
    line: Int,
    verdict: DeclarationVerdict = DeclarationVerdict.Allowed,
    rules: List<RuleRef> = if (verdict == DeclarationVerdict.Unexpected) emptyList() else listOf(RuleRef("${kind.jsonName} $name", DeclarationSiteRef("Role.kt", 10))),
    difference: String? = null,
): DeclarationModel = DeclarationModel(kind, name, line, verdict, rules, difference)

/** katachi's explanation of [file]; restricting top-level declarations unless [declarationsChecked] says otherwise. */
internal fun fileDeclarations(
    file: String,
    vararg declarations: DeclarationModel,
    declarationsChecked: Boolean = true,
    missing: List<RuleRef> = emptyList(),
    unexplainedReason: String? = null,
): FileDeclarationsModel = FileDeclarationsModel(file, declarationsChecked, declarations.toList(), missing, unexplainedReason)

/** [declarations] of [role]'s `ktFileContent { }` constraint, as `fileDeclarationsOf` returns it. */
internal fun roleFileDeclarations(
    role: RoleModel,
    declarations: FileDeclarationsModel,
    freshness: Freshness = Freshness.Fresh,
): RoleFileDeclarations = RoleFileDeclarations(
    role = role,
    constraint = constraintModel(name = "ktFileContent", backend = ConstraintBackend.KtFileContent, fileDeclarations = listOf(declarations)),
    declarations = declarations,
    freshness = freshness,
)
