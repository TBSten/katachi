package me.tbsten.katachi.benchmark.util

import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation

/** Run once in `@Setup`, so a benchmark that stopped matching its project fails instead of timing an error path. */
internal fun requireViolations(violations: List<Violation>, expected: List<String>) {
    val actual = violations.map { "[${it.label}] ${it.path}" }.sorted()
    check(actual == expected.sorted()) {
        "The benchmark's project no longer checks as expected.\n" +
            "expected: ${expected.take(5)} (${expected.size})\n" +
            "actual:   ${actual.take(5)} (${actual.size})"
    }
}

/** What `assert()` does with the violations `validate()` returned: fail on any `Error`. */
internal fun assertNoErrors(violations: List<Violation>) {
    val errors = violations.filter { it.severity == Severity.Error }
    check(errors.isEmpty()) {
        "assert() would fail: ${errors.take(5).map { "[${it.label}] ${it.path}" }} (${errors.size})"
    }
}
