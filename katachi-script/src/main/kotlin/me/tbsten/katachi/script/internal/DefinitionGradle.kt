package me.tbsten.katachi.script.internal

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.isRegularFile

/** Runs the project's `gradlew` with [arguments] in [root] and returns everything it printed, stderr included. */
internal fun interface GradleRunner {
    fun run(root: Path, arguments: List<String>): GradleRun
}

internal class GradleRun(val status: Int, val output: String)

internal object ProcessGradleRunner : GradleRunner {
    override fun run(root: Path, arguments: List<String>): GradleRun {
        val process = ProcessBuilder(listOf(root.resolve(gradlewName()).toString()) + arguments)
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        // Nothing to answer: a prompt must not wait on a terminal that lint does not hand over.
        process.outputStream.close()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        return GradleRun(process.waitFor(), output)
    }
}

private const val DEFINITION_JSON_TASK: String = "katachiInternalDefinitionJson"

/** Where the Gradle plugin writes the task's JSON, under the module directory; the IDE plugin reads the same place. */
private const val DEFINITION_JSON_PATH: String = "build/katachi/internalDefinitionJson/definition.json"

private const val COMPILE_ERRORS_SHOWN: Int = 10

private const val OUTPUT_TAIL_LINES: Int = 15

private fun gradlewName(): String = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "gradlew.bat" else "gradlew"

/**
 * The definition of [module] as Gradle evaluates it: runs `katachiInternalDefinitionJson` and reads the JSON it wrote.
 * Stops (1) with the reason when Gradle cannot run, the definition does not compile, or the JSON cannot be read.
 */
internal fun ScriptContext.evaluateDefinition(projectRoot: Path, module: ModuleDir): DefinitionModel {
    if (!projectRoot.resolve(gradlewName()).isRegularFile()) die(Messages.Lint.gradlewMissing(fileUri(projectRoot)))
    val task = "${module.gradlePath}:$DEFINITION_JSON_TASK"
    val arguments = listOf("-q", "--console=plain", task)
    val shown = "./${gradlewName()} $task"
    err(pick(Messages.Lint.evaluatingDefinition(shown)))
    val started = Instant.now()
    val run = gradle.run(projectRoot, arguments)
    if (run.status != 0) {
        if (taskIsMissing(run.output)) die(Messages.Lint.definitionTaskMissing(module.gradlePath))
        die(Messages.Lint.definitionNotEvaluated(shown, run.status, failureDetails(run.output)))
    }
    val json = projectRoot.resolve(module.dir).resolve(DEFINITION_JSON_PATH).normalize()
    // The task keeps the file of the last good run when the definition fails, so an old file is not an answer.
    if (!json.isRegularFile() || Files.getLastModifiedTime(json).toInstant().isBefore(started.minusSeconds(2))) {
        die(Messages.Lint.definitionJsonUnreadable(fileUri(json), pick(Messages.Lint.jsonNotWritten)))
    }
    return try {
        readDefinitionModel(String(Files.readAllBytes(json), Charsets.UTF_8))
    } catch (tooNew: DefinitionJsonTooNewException) {
        die(Messages.Lint.definitionJsonTooNew(tooNew.version, SUPPORTED_DEFINITION_SCHEMA_VERSION))
    } catch (broken: DefinitionJsonFormatException) {
        die(Messages.Lint.definitionJsonUnreadable(fileUri(json), broken.message))
    } catch (io: IOException) {
        die(Messages.Lint.definitionJsonUnreadable(fileUri(json), io.message))
    }
}

private fun taskIsMissing(output: String): Boolean =
    output.contains(DEFINITION_JSON_TASK) && Regex("not found in (root )?project|Cannot locate tasks that match|Task '[^']*' not found").containsMatchIn(output)

/** Kotlin's `e: file:///...` lines when there are any; otherwise the end of what Gradle printed. */
private fun ScriptContext.failureDetails(output: String): String {
    val lines = output.lines()
    val errors = lines.filter { it.startsWith("e: ") }.distinct()
    if (errors.isNotEmpty()) {
        val shown = errors.take(COMPILE_ERRORS_SHOWN).joinToString("\n") { "  $it" }
        val rest = errors.size - COMPILE_ERRORS_SHOWN
        return pick(Messages.Lint.compileErrors(errors.size, if (rest > 0) shown + "\n" + pick(Messages.Lint.moreErrors(rest)) else shown))
    }
    val tail = lines.filter { it.isNotBlank() }.takeLast(OUTPUT_TAIL_LINES).joinToString("\n") { "  $it" }
    return pick(Messages.Lint.gradleOutputTail(tail))
}
