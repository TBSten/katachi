package me.tbsten.katachi.test.report

import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationDetail
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.report.toSarif

/** The SARIF 2.1.0 schema shipped next to the goldens, loaded once. */
private fun sarifSchema(): Schema {
    val text = checkNotNull(SarifSchemaSpec::class.java.classLoader.getResourceAsStream("sarif/sarif-schema-2.1.0.json")) {
        "sarif/sarif-schema-2.1.0.json is missing from the test resources"
    }.use { it.readBytes().toString(Charsets.UTF_8) }
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(text)
}

private fun Schema.problemsIn(sarif: String): List<String> = validate(sarif, InputFormat.JSON).map { it.toString() }

/**
 * Every document `toSarif` writes has to pass the SARIF 2.1.0 JSON Schema: the golden, the
 * empty document, and the combinations of violation type, severity and where the location
 * ends up that a golden over one repository cannot all reach.
 */
class SarifSchemaSpec : FreeSpec({
    val schema = sarifSchema()
    val tree = sarifRepository()

    "golden と空の文書が schema を通る" {
        withClue("every kind") { schema.problemsIn(everyKindOfViolation().toSarif(tree.context())).shouldBeEmpty() }
        withClue("empty") { schema.problemsIn(emptyList<Violation>().toSarif(tree.context())).shouldBeEmpty() }
    }

    "位置の受け皿ごとに schema を通る" - {
        val contexts = mapOf(
            "source roots あり・architecture { } あり" to tree.context(),
            "source roots 無し" to tree.context(sourceRoots = emptyList()),
            "architecture { } の位置が無い" to tree.context(architectureDeclaredAt = null),
            "source roots も architecture { } も無い" to tree.context(sourceRoots = emptyList(), architectureDeclaredAt = null),
            "source root が外を指す" to tree.context(sourceRoots = listOf(FsPath.of("/elsewhere"))),
            "git の接頭辞が無い" to tree.context(repositoryPrefix = ""),
            "git の接頭辞がプロジェクトルートと合わない" to tree.context(repositoryPrefix = "ios/"),
        )
        for ((name, context) in contexts) {
            name {
                schema.problemsIn(everyKindOfViolation().toSarif(context)).shouldBeEmpty()
            }
        }
    }

    "外部の違反の kind × severity × details の有無の組み合わせが schema を通る" {
        val violations = buildList {
            for (kind in ViolationKind.entries) {
                for (severity in Severity.entries) {
                    for (withDetails in listOf(true, false)) {
                        add(
                            ForeignSarifViolation(
                                path = if (withDetails) "app/src/main/kotlin/com/example/Broken.kt" else ".",
                                label = "Rule${kind.name}${severity.name}${if (withDetails) "Detailed" else ""}",
                                details = if (withDetails) listOf(ViolationDetail("Rule", "x"), ViolationDetail("改行\n入り", "値")) else emptyList(),
                                kind = kind,
                                severity = severity,
                            ),
                        )
                    }
                }
            }
        }
        schema.problemsIn(violations.toSarif(tree.context())).shouldBeEmpty()
    }

    "外部 backend が line に 0 や負の値を入れた UnsatisfiedFileConstraint でも schema を通る" {
        val roles = SarifRoles()
        val violations = listOf(0, -1).map { line ->
            UnsatisfiedFileConstraint(
                path = "core/domain/src/main/kotlin/com/example/domain/GetUserUseCase.kt",
                declaration = "GetUserUseCase",
                line = line,
                role = roles.useCase,
                constraintName = "line $line",
                layoutPath = null,
                declaredAt = DeclarationSite("UseCaseRole.kt", 16),
            )
        }
        schema.problemsIn(violations.toSarif(tree.context())).shouldBeEmpty()
    }

    "空白や # を含むパスの文書が schema を通る" {
        val odd = FakeSarifTree((SARIF_PROJECT_ROOT / "My App/src/Foo#1.kt").value to "")
        val violation = ForeignSarifViolation(path = "My App/src/Foo#1.kt", label = "OddPath")
        schema.problemsIn(listOf(violation).toSarif(odd.context())).shouldBeEmpty()
    }
})
