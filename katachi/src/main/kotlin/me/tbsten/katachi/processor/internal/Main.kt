package me.tbsten.katachi.processor.internal

import kotlin.system.exitProcess
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.processor.KatachiEntryPointNotFoundException

/**
 * The entry point every `katachi<Key>` task of the Gradle plugin starts.
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
 * Each `katachi<Key>` task passes exactly one `--processor`: the key it was registered for. The
 * plugin's `katachiProcessors` task passes several, so that they share this one JVM and one walk
 * of the project, and hands each processor its build-script arguments with `--arg-for`.
 *
 * The exit happens **after** [runProcessors] has printed its full report, never before: the run's
 * summary is the most useful thing on the screen, and returning a failing exit status first would
 * cut the process off before `out` finished writing it.
 *
 * An exception thrown anywhere above is not caught here. It ends the JVM with a non-zero status,
 * and `JavaExec` turns that into a failed task -- so a run that could not even get as far as
 * printing a report still fails loudly rather than succeeding having done nothing.
 *
 * ## Why it is internal
 *
 * Nothing calls this by its Kotlin name. The JVM finds it as `MainKt.main(String[])`, and a
 * top level `internal` function is still `public static` in bytecode under its own name -- only
 * members of a class get their names mangled. So it stays out of the public API, where a user
 * could otherwise call a function that ends their process with `exitProcess`.
 *
 * @param args the command line a `katachi<Key>` task passes on. See
 *   [parseProcessorCommandLine] for the accepted form.
 */
internal fun main(args: Array<String>) {
    val status = runMain(args)
    if (status != 0) exitProcess(status)
}

/**
 * [main] without the exit, so that a spec can run it and read the status.
 *
 * ## The IDE plugin's report
 *
 * A run given `--definition-errors=<path>` writes `definition-errors.json` there at its end,
 * whatever happened: the mistakes of the definition that stopped it, found while the entry point
 * was initialized or while a processor ran, or `errors: []`. A definition that cannot even be
 * built is reported as itself, with its stack trace on [err] and status 1, rather than as an
 * entry point that was not found. That mistake is recorded and then thrown as it is, with no
 * wrapper, so the JVM ends with `Exception in thread "main" <its class>: <its message>` and
 * status 1, which is what the IDE plugin classifies the failure from when it has no file to read.
 * Anything else thrown above ends the JVM the same way.
 *
 * @param err where the warning of a report that could not be written goes.
 * @param fileSystem and [out]: what the processors run against, and where their report goes.
 * @param classLoader where the entry point is looked up. Specs pass a fresh one per run, since a
 *   class whose initialization failed once cannot be initialized again in the same loader.
 * @return `0`, or `1` when a processor failed.
 * @throws Throwable the mistake of a definition that could not be built, as it is (its
 *   `definition-errors.json` is written first).
 */
internal fun runMain(
    args: Array<String>,
    err: (String) -> Unit = System.err::println,
    fileSystem: KatachiFileSystem = RealFileSystem(),
    out: (String) -> Unit = ::println,
    classLoader: ClassLoader = KatachiEntryPoint::class.java.classLoader,
): Int {
    val commandLine = parseProcessorCommandLine(args)
    val mistakes = mutableListOf<DefinitionErrorEntry>()
    try {
        val entryPoint = try {
            loadEntryPoint(commandLine.entryPointClassName, classLoader)
        } catch (failure: Throwable) {
            if (definitionErrorsOf(failure).isEmpty()) throw failure
            mistakes += DefinitionErrorEntry(DefinitionErrorPhase.EntryPoint, processor = null, exception = failure)
            throw failure
        }
        val summary = runProcessors(
            architecture = entryPoint.architecture,
            registry = entryPoint.processors,
            processorKeys = commandLine.processorKeys,
            rawArgs = commandLine.args,
            argsFor = commandLine.argsFor,
            fileSystem = fileSystem,
            out = out,
        )
        summary.failures.mapTo(mistakes) { DefinitionErrorEntry(DefinitionErrorPhase.Processor, it.key, it.cause) }
        return if (summary.failed > 0) 1 else 0
    } finally {
        commandLine.definitionErrorsPath?.let { path -> writeDefinitionErrors(path, mistakes, err) }
    }
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
 * @throws me.tbsten.katachi.KatachiDeclarationException when initializing the object ran
 *   `architecture { }` and it was the definition that threw; thrown as it is, not wrapped.
 */
internal fun loadEntryPoint(
    className: String,
    classLoader: ClassLoader = KatachiEntryPoint::class.java.classLoader,
): KatachiEntryPoint {
    val instance = runCatching {
        val type = Class.forName(className, true, classLoader)
        type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
    }.getOrElse { cause ->
        // `architecture { }` threw while the object was initialized: the definition is wrong, and
        // saying that the entry point was not found would send the reader to the wrong place.
        throw definitionFailureIn(cause) ?: KatachiEntryPointNotFoundException(className, cause)
    }

    return instance as? KatachiEntryPoint
        ?: throw KatachiEntryPointNotFoundException(className, cause = null)
}
