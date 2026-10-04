package me.tbsten.katachi.report.internal

import me.tbsten.katachi.check.AmbiguousLayout
import me.tbsten.katachi.check.MissingDescription
import me.tbsten.katachi.check.MissingFile
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.UncheckedCheck
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.STEP
import me.tbsten.katachi.check.internal.blockOf
import me.tbsten.katachi.check.internal.reportedDeclaredAt
import me.tbsten.katachi.report.SarifContext

private const val SARIF_SCHEMA: String = "https://json.schemastore.org/sarif-2.1.0.json"
private const val SARIF_VERSION: String = "2.1.0"
private const val TOOL_NAME: String = "katachi"
private const val INFORMATION_URI: String = "https://tbsten.github.io/katachi/"

/**
 * The most results one run may carry and still be accepted by GitHub code scanning, which
 * rejects the whole upload -- every result, not the ones over the line -- past it. From
 * "SARIF results file limits", https://docs.github.com/en/code-security/code-scanning/
 * integrating-with-code-scanning/sarif-support-for-code-scanning (Results per run: 25,000).
 * `maxViolations` does not apply to the report; this is the one cut it makes.
 */
internal const val GITHUB_MAX_RESULTS: Int = 25_000

/**
 * The SARIF 2.1.0 document for [violations]: one run, its rules, its results and, when a
 * declaration site could not be placed, the notifications saying why.
 *
 * Keys are written in one fixed order and nothing that varies between two runs over the same
 * input -- a time stamp, a fingerprint, a machine-specific path inside a message -- goes in, so
 * that a golden file can hold the whole output.
 */
internal fun sarifDocumentOf(violations: List<Violation>, context: SarifContext): SarifJson {
    val base = RepositoryBase(context.projectRoot, context.repositoryPrefix)
    val kept = resultsWithinGitHubLimit(violations)
    val rules = sarifRulesOf(kept)
    val ruleIndex = rules.withIndex().associate { (index, rule) -> rule.id to index }
    val resolver = SarifLocationResolver(context, base)
    // Results first: resolving them is what fills the notifications the invocation lists.
    val results = kept.map { violation -> resultOf(violation, ruleIndex, resolver.locationsOf(violation)) }
    val notifications = resolver.notifications + listOfNotNull(truncationNoteOf(violations.size - kept.size))
    val run = sarifObject {
        put("tool", sarifObject { put("driver", driverOf(rules, context.toolVersion)) })
        put("originalUriBaseIds", sarifObject { put(SRCROOT, sarifObject { put("uri", base.uri) }) })
        put("invocations", listOf(invocationOf(notifications)))
        put("results", results)
    }
    return sarifObject {
        put("\$schema", SARIF_SCHEMA)
        put("version", SARIF_VERSION)
        put("runs", listOf(run))
    }
}

/**
 * [violations] as they are while they fit in one run; past [GITHUB_MAX_RESULTS], the errors
 * first and then the warnings, in their original order within each, cut at the limit -- so
 * that what GitHub drops is the least severe, and what it shows is what the baseline would
 * have to hold back.
 */
private fun resultsWithinGitHubLimit(violations: List<Violation>): List<Violation> {
    if (violations.size <= GITHUB_MAX_RESULTS) return violations
    // `sortedBy` is stable, so each severity keeps the order `assertWith` handed over.
    return violations.sortedBy { it.severity != Severity.Error }.take(GITHUB_MAX_RESULTS)
}

private fun truncationNoteOf(leftOut: Int): String? {
    if (leftOut <= 0) return null
    val count = if (leftOut == 1) "1 result was" else "$leftOut results were"
    return "$count left out: GitHub code scanning accepts at most $GITHUB_MAX_RESULTS results per run, " +
        "and refuses the whole file past that. The error-level results were kept first, then the warnings. " +
        "Hold the known violations back with a baseline (-Dkatachi.baseline.update) to bring the count down."
}

private fun driverOf(rules: List<SarifRule>, version: String): SarifJson = sarifObject {
    put("name", TOOL_NAME)
    put("semanticVersion", version)
    put("informationUri", INFORMATION_URI)
    put(
        "rules",
        rules.map { rule ->
            sarifObject {
                put("id", rule.id)
                put("name", rule.name)
                put("shortDescription", sarifObject { put("text", rule.shortDescription) })
                put("defaultConfiguration", sarifObject { put("level", rule.level) })
            }
        },
    )
}

private fun invocationOf(notifications: List<String>): SarifJson = sarifObject {
    put("executionSuccessful", true)
    put(
        "toolExecutionNotifications",
        notifications.map { text ->
            sarifObject {
                put("level", "warning")
                put("message", sarifObject { put("text", text) })
            }
        },
        omitWhenEmpty = true,
    )
}

private fun resultOf(violation: Violation, ruleIndex: Map<String, Int>, locations: SarifLocations): SarifJson {
    val ruleId = sarifRuleIdOf(violation)
    return sarifObject {
        put("ruleId", ruleId)
        put("ruleIndex", ruleIndex.getValue(ruleId))
        put("level", sarifLevelOf(violation.severity))
        put("message", sarifObject { put("text", sarifMessageOf(violation)) })
        put("locations", listOf(sarifObject { put("physicalLocation", physicalLocationOf(locations.primary)) }))
        put(
            "relatedLocations",
            locations.related.mapIndexed { index, related ->
                sarifObject {
                    put("id", index + 1)
                    put("physicalLocation", physicalLocationOf(related.location))
                    put("message", sarifObject { put("text", related.message) })
                }
            },
            omitWhenEmpty = true,
        )
        put("properties", propertiesOf(violation))
    }
}

private fun physicalLocationOf(location: SarifLocation): SarifJson = sarifObject {
    put(
        "artifactLocation",
        sarifObject {
            put("uri", location.uri)
            put("uriBaseId", location.uriBaseId)
        },
    )
    location.startLine?.let { put("region", sarifObject { put("startLine", it) }) }
}

/**
 * The report block of [violation] without its `[label] path` line and without the one step of
 * indent every other line of a block carries. Paths stay relative -- `root = null` -- because a
 * `file:///` URI would differ from one machine to the next. A block that is only its first line
 * keeps that line: GitHub refuses an empty message.
 */
internal fun sarifMessageOf(violation: Violation): String {
    val block = blockOf(violation, root = null)
    val rest = block.drop(1).map { it.removePrefix(STEP) }
    return if (rest.isEmpty()) block.first() else rest.joinToString("\n")
}

/** What the violation is made of, for whoever reads the file by machine. A `null` value is left out. */
private fun propertiesOf(violation: Violation): SarifJson = sarifObject {
    put("kind", violation.kind.name)
    put("severity", violation.severity.name)
    put("path", violation.path)
    when (violation) {
        is MissingFile -> {
            put("role", violation.role.qualifiedName)
            put("declaredAt", violation.declaredAt.toString())
        }

        is UnsatisfiedFileConstraint -> {
            put("role", violation.role.qualifiedName)
            put("constraint", violation.constraintName)
            put("declaration", violation.declaration)
            put("layoutPath", violation.layoutPath)
            put("declaredAt", violation.reportedDeclaredAt.toString())
        }

        is UncheckedFileConstraint -> {
            put("role", violation.role.qualifiedName)
            put("constraint", violation.constraintName)
            put("layoutPath", violation.layoutPath)
            put("declaredAt", violation.declaredAt.toString())
        }

        is MissingDescription -> {
            put("role", violation.role.qualifiedName)
            put("declaredAt", violation.declaredAt.toString())
        }

        is AmbiguousLayout -> {
            put("role", violation.claims.joinToString(",") { it.role.qualifiedName })
            put("declaredAt", violation.claims.firstOrNull()?.declaredAt?.toString())
        }

        is UncheckedCheck -> put("check", violation.check)

        is StaleBaselineEntry -> {
            put("check", violation.check)
            put("baseline", violation.baselinePath)
        }

        else -> Unit
    }
}
