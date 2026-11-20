package me.tbsten.katachi.processor.internal

import java.io.DataInputStream
import kotlin.system.exitProcess
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.KatachiEntryPointNotFoundException
import me.tbsten.katachi.processor.KatachiProcessorJvmTooOldException

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
        // `architecture { }` threw while the object was initialized, or this JVM is older than the
        // Java the tests were compiled for: saying that the entry point was not found would send
        // the reader to the wrong place.
        throw definitionFailureIn(cause)
            ?: jvmTooOldFailureIn(cause, className, classLoader)
            ?: KatachiEntryPointNotFoundException(className, cause)
    }

    return instance as? KatachiEntryPoint
        ?: throw KatachiEntryPointNotFoundException(className, cause = null)
}

// HotSpot names the refused class and the version of its class file:
// "com/example/Foo has been compiled by a more recent version of the Java Runtime (class file version 65.0), ...".
private val MORE_RECENT_CLASS_FILE =
    Regex("""^(\S+) has been compiled by a more recent version of the Java Runtime \(class file version (\d+)\.""")

private val CLASS_FILE_MAGIC: Int = 0xCAFEBABE.toInt()

private fun jvmTooOldFailureIn(
    failure: Throwable,
    entryPointClassName: String,
    classLoader: ClassLoader,
): KatachiProcessorJvmTooOldException? {
    val refused = generateSequence(failure) { it.cause }.filterIsInstance<UnsupportedClassVersionError>().firstOrNull()
        ?: return null
    val match = refused.message?.let { MORE_RECENT_CLASS_FILE.find(it) }
    val className = match?.groupValues?.get(1)?.replace('/', '.') ?: entryPointClassName
    val compiledFor = (match?.groupValues?.get(2)?.toIntOrNull() ?: classFileMajorVersionOf(className, classLoader))
        ?.let(::javaVersionOf)
    val runsOn = Runtime.version().feature()
    // Refused for another reason, such as preview features: the version is not what is wrong.
    if (compiledFor != null && compiledFor <= runsOn) return null
    return KatachiProcessorJvmTooOldException(className, compiledFor, runsOn, failure)
}

private fun classFileMajorVersionOf(className: String, classLoader: ClassLoader): Int? = catching {
    classLoader.getResourceAsStream(className.replace('.', '/') + ".class")?.use { stream ->
        val header = DataInputStream(stream)
        if (header.readInt() != CLASS_FILE_MAGIC) return@use null
        header.readUnsignedShort()
        header.readUnsignedShort()
    }
}.getOrNull()

// Class file version 52 is Java 8, 65 is Java 21.
private fun javaVersionOf(classFileMajorVersion: Int): Int = classFileMajorVersion - 44
