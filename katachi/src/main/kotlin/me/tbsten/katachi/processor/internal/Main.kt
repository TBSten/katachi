package me.tbsten.katachi.processor.internal

import kotlin.system.exitProcess
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.processor.KatachiEntryPointNotFoundException

// The JVM name me.tbsten.katachi.processor.MainKt is written into the plugin as a string; moving this file
// breaks the task without breaking the build.
internal fun main(args: Array<String>) {
    val status = runMain(args)
    if (status != 0) exitProcess(status)
}

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
