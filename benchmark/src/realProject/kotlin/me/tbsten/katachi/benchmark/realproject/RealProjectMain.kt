package me.tbsten.katachi.benchmark.realproject

import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem

/** The real projects this runner knows a definition for, by the name given as `--name`. */
private val definitions: Map<String, () -> Architecture> = mapOf(
    "nowinandroid" to ::nowInAndroidArchitecture,
)

/**
 * Measures `validate()` on a real project checked out at `--dir`, and writes the times as
 * github-action-benchmark's `customSmallerIsBetter` JSON to `--output`.
 *
 * `validate()` rather than `assert()`: the definitions are coarse and the project is not
 * katachi's, so violations are expected and must not end the run. Four values come out:
 * the first call of each kind in a fresh JVM (cold: class loading, the JIT and, for the
 * Konsist one, Konsist's own start-up) and the median of `--iterations` calls after
 * `--warmups` more (warm). The layout-only run always goes first, so the Konsist cold value
 * is Konsist's start-up on top of an already warm walk.
 */
fun main(args: Array<String>) {
    val options = args.associate { arg ->
        val (key, value) = arg.removePrefix("--").split("=", limit = 2).takeIf { it.size == 2 }
            ?: throw IllegalArgumentException("Arguments are --key=value, got: $arg")
        key to value
    }
    val name = options["name"] ?: "nowinandroid"
    val definition = definitions[name]
        ?: throw IllegalArgumentException(
            "No definition for the real project '$name'. Known: ${definitions.keys.joinToString()}",
        )
    val directory = File(
        options["dir"] ?: throw IllegalArgumentException(
            "--dir (-PrealProject.dir) is required: the checkout of '$name' to measure.",
        ),
    ).absoluteFile
    require(directory.isDirectory) { "No such directory: file://${directory.path}" }
    val output = File(options["output"] ?: "build/results/real-project/$name.json").absoluteFile
    val warmups = options["warmups"]?.toInt() ?: 3
    val iterations = options["iterations"]?.toInt() ?: 10

    val architecture = definition()
    val fileSystem = RealFileSystem(directory)
    val layoutOnly = { architecture.validate(fileSystem) }
    val withKonsist = { architecture.validate(fileSystem, FileConstraintCheck()) }

    val (layoutViolations, layoutCold) = measureTimedValue(layoutOnly)
    val (konsistViolations, konsistCold) = measureTimedValue(withKonsist)
    repeat(warmups) {
        layoutOnly()
        withKonsist()
    }
    val layoutWarm = medianOf(iterations) { measureTimedValue(layoutOnly).duration }
    val konsistWarm = medianOf(iterations) { measureTimedValue(withKonsist).duration }

    val extra = """
        violations: ${layoutViolations.size} (layout) / ${konsistViolations.size} (with Konsist)
        ${layoutViolations.countByLabel()}
        warmups: $warmups, iterations: $iterations
    """.trimIndent()
    val results = listOf(
        Result("$name validate() cold", layoutCold, extra),
        Result("$name validate() warm median", layoutWarm, extra),
        Result("$name validate(FileConstraintCheck()) cold", konsistCold, extra),
        Result("$name validate(FileConstraintCheck()) warm median", konsistWarm, extra),
    )
    output.parentFile.mkdirs()
    output.writeText(results.joinToString(",\n", prefix = "[\n", postfix = "\n]\n") { it.toJson() })
    results.forEach { println("${it.name}: ${it.millis} ms") }
    println(extra)
    println("Wrote file://${output.path}")
}

private class Result(val name: String, duration: Duration, val extra: String) {
    val millis: Double = duration.toDouble(DurationUnit.MILLISECONDS)

    fun toJson(): String =
        """  {"name": ${name.jsonString()}, "unit": "ms", "value": $millis, "extra": ${extra.jsonString()}}"""
}

private fun medianOf(times: Int, measure: () -> Duration): Duration {
    require(times > 0) { "--iterations must be at least 1, got $times" }
    val sorted = List(times) { measure() }.sorted()
    return if (times % 2 == 1) sorted[times / 2] else (sorted[times / 2 - 1] + sorted[times / 2]) / 2
}

private fun List<Violation>.countByLabel(): String =
    groupingBy { it.label }.eachCount().entries
        .sortedByDescending { it.value }
        .joinToString { "${it.key}: ${it.value}" }

private fun String.jsonString(): String = buildString {
    append('"')
    for (c in this@jsonString) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}
