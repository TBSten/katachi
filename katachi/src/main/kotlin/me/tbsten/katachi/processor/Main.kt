package me.tbsten.katachi.processor

import kotlin.system.exitProcess
import me.tbsten.katachi.InternalKatachiApi

/**
 * The entry point the Gradle plugin's `runKatachiProcessor` task starts.
 *
 * The task is a `JavaExec` and not a `Test`, so no test engine is on the way in: the JVM is
 * started with the applying module's test runtime classpath and this function is the only thing
 * that runs. Its JVM name, `me.tbsten.katachi.processor.MainKt`, is written into the plugin as a
 * string, so moving this file breaks the task without breaking the build — a sample run is what
 * catches it.
 *
 * ## What it does
 *
 * 1. [parseProcessorCommandLine]s [args].
 * 2. [loadEntryPoint]s the generated [KatachiEntryPoint] the plugin named with `--entry-point`.
 * 3. [runProcessors] every selected key against that entry point's `architecture`, with the
 *    `--arg` values.
 * 4. Exits with a non-zero status if any processor failed.
 *
 * The exit happens **after** [runProcessors] has printed its full report, never before: the run's
 * summary is the most useful thing on the screen, and returning a failing exit status first would
 * cut the process off before `out` finished writing it.
 *
 * An exception thrown anywhere above is not caught here. It ends the JVM with a non-zero status,
 * and `JavaExec` turns that into a failed task -- so a run that could not even get as far as
 * printing a report still fails loudly rather than succeeding having done nothing.
 *
 * ## Example 1: run the entry point the way the Gradle task does
 * ```kt
 * @OptIn(InternalKatachiApi::class)
 * object MyEntryPoint : KatachiEntryPoint {
 *     override val architecture: Architecture = architecture {
 *         "domain".group { "UseCase" { layout { "domain" / "*UseCase.kt".file() } } }
 *     }
 *     override val processors: Map<String, Class<*>> = mapOf(
 *         "layout" to LayoutCheck::class.java,
 *     )
 * }
 *
 * @OptIn(InternalKatachiApi::class)
 * fun runIt() {
 *     main(arrayOf("--entry-point=com.example.MyEntryPoint", "--processor=layout"))
 * }
 * ```
 *
 * @param args the command line the `runKatachiProcessor` task passes on. See
 *   [parseProcessorCommandLine] for the accepted form.
 */
@InternalKatachiApi
public fun main(args: Array<String>) {
    val commandLine = parseProcessorCommandLine(args)
    val entryPoint = loadEntryPoint(commandLine.entryPointClassName)
    val summary = runProcessors(
        architecture = entryPoint.architecture,
        registry = entryPoint.processors,
        processorKeys = commandLine.processorKeys,
        rawArgs = commandLine.args,
    )
    if (summary.failed > 0) exitProcess(1)
}

/**
 * Reads the Gradle plugin's generated [KatachiEntryPoint] off the test runtime classpath.
 *
 * The one reflective step of the whole path: `Class.forName` finds the generated class by name,
 * and its Kotlin `object`-ness guarantees an `INSTANCE` field. Everything read off the result
 * afterwards -- `architecture`, `processors` -- goes through the typed [KatachiEntryPoint]
 * interface instead.
 *
 * @throws KatachiEntryPointNotFoundException when [className] cannot be loaded, has no
 *   `INSTANCE` field, or that field is not a [KatachiEntryPoint].
 */
internal fun loadEntryPoint(className: String): KatachiEntryPoint {
    val instance = runCatching {
        val type = Class.forName(className, true, KatachiEntryPoint::class.java.classLoader)
        type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
    }.getOrElse { cause -> throw KatachiEntryPointNotFoundException(className, cause) }

    return instance as? KatachiEntryPoint
        ?: throw KatachiEntryPointNotFoundException(className, cause = null)
}
