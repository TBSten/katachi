package me.tbsten.katachi.script.internal

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.isRegularFile
import kotlin.io.path.toPath
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.script.KatachiScript

internal const val KATACHI_SCRIPT_EXTENSION = ".katachi.kts"

/** Set by `katachi <file>.katachi.kts` on the `kotlin` it starts, so the script knows where it lives. */
internal const val KATACHI_SCRIPT_FILE_ENV = "KATACHI_SCRIPT_FILE"

internal object KatachiScriptCompilationConfiguration : ScriptCompilationConfiguration({
    defaultImports(
        "me.tbsten.katachi.script.*",
        "me.tbsten.katachi.script.command.*",
        "me.tbsten.katachi.script.error.*",
        "me.tbsten.katachi.script.option.*",
    )
    jvm {
        // Named jars, not the whole class context: in the IDE the definition's class loader has the
        // IDE's own class loader as its parent, whose classpath a script must not see.
        updateClasspath(scriptCompilationClasspath().map(Path::toFile))
    }
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
})

internal fun scriptDirectoryOf(env: Map<String, String>): Path? =
    env[KATACHI_SCRIPT_FILE_ENV]?.takeIf { it.isNotBlank() }?.let { Path(it).absolute().parent }

// One class from each jar a `*.katachi.kts` needs. The Kotlin standard library and the scripting
// API are left out: `kotlin` and the IDE bring their own, and under `kotlin katachi.main.kts` the
// scripting API lives in kotlin-main-kts.jar, whose own script definition must not come along.
private val SCRIPT_CLASSPATH_MARKERS: List<KClass<*>> = listOf(
    KatachiScript::class,
    Architecture::class,
    KSerializer::class,
    Json::class,
)

internal fun scriptClasspath(): List<Path> = SCRIPT_CLASSPATH_MARKERS.mapNotNull(::locationOf).distinct()

// What a `*.katachi.kts` compiles against: the jars above and the standard library of whoever loaded
// the definition (`kotlin`, the IDE, or a build).
private fun scriptCompilationClasspath(): List<Path> = (scriptClasspath() + listOfNotNull(locationOf(Unit::class))).distinct()

private fun locationOf(marker: KClass<*>): Path? = marker.java.protectionDomain?.codeSource?.location?.toURI()?.toPath()

/** `katachi internal <name>`: subcommands for katachi's own tools (the IDE plugin), left out of --help. */
internal fun ScriptContext.runInternal(args: List<String>): Int = when (args.firstOrNull()) {
    "script-classpath" -> {
        scriptClasspath().forEach { say(it.toString()) }
        0
    }

    else -> dieUsage(Messages.ScriptFile.unknownInternalSubcommand(args.firstOrNull().orEmpty()))
}

/** `katachi <file>.katachi.kts [args...]`: compiles and runs the script with `kotlin`, on the jars of this katachi. */
internal fun ScriptContext.runKatachiScriptFile(file: String, args: List<String>): Int {
    val script = resolve(file)
    if (!script.isRegularFile()) {
        die(Messages.ScriptFile.noSuchScript(fileUriOf(script)))
    }
    val classpath = scriptClasspath().joinToString(pathSeparatorOf(windows).toString())
    // -no-reflect: with a definition from -cp, `kotlin` 2.4.21 puts kotlin-reflect where the standard library
    // is not visible, and the first `::class` fails with NoClassDefFoundError. katachi needs no kotlin-reflect.
    // `kotlin` is `kotlin.bat` on Windows, which a process cannot start by the name without its extension.
    // The batch file splits its arguments at `;` too, so the class path (`a.jar;b.jar`) is quoted on Windows, as one argument.
    val command = listOf(
        if (windows) "kotlin.bat" else "kotlin",
        "-no-reflect",
        "-cp",
        if (windows) "\"$classpath\"" else classpath,
        script.toString(),
    ) + args
    return try {
        launch.launch(command, mapOf(KATACHI_SCRIPT_FILE_ENV to script.toString()), cwd)
    } catch (_: IOException) {
        die(Messages.ScriptFile.kotlinNotPathInstall)
    }
}

/** Starts a process and waits for it. A seam for the tests, which cannot start `kotlin`. */
internal fun interface ProcessLauncher {
    /** @throws IOException when the program cannot be started at all. */
    fun launch(command: List<String>, extraEnv: Map<String, String>, cwd: Path): Int
}

internal object InheritingIoLauncher : ProcessLauncher {
    override fun launch(command: List<String>, extraEnv: Map<String, String>, cwd: Path): Int {
        val builder = ProcessBuilder(command).directory(cwd.toFile()).inheritIO()
        dropKotlinBatchState(command, builder.environment())
        builder.environment().putAll(extraEnv)
        return builder.start().waitFor()
    }
}

/**
 * Takes out of [environment] what the `kotlin.bat` that started this katachi left there, when [command] is another `kotlin.bat`.
 *
 * The batch file collects its arguments in the variable `KOTLIN_OPTS`, and that variable stays in the environment of the `java` it
 * starts (a `setlocal` only undoes it for the batch file itself). A second `kotlin.bat` started from here would go on from it, and
 * so run `katachi.main.kts` again with the arguments of the first one, before the script it was asked to run.
 */
internal fun dropKotlinBatchState(command: List<String>, environment: MutableMap<String, String>) {
    if (command.firstOrNull()?.endsWith(".bat", ignoreCase = true) == true) {
        environment.keys.removeAll { it.equals("KOTLIN_OPTS", ignoreCase = true) }
    }
}
