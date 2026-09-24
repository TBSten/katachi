package me.tbsten.katachi.processor

import java.io.File
import me.tbsten.katachi.InternalKatachiApi

/**
 * Where the walking skeleton writes its one file, relative to the JVM's working directory.
 *
 * `JavaExec` starts the process in the directory of the project the task belongs to, so this
 * lands in that module's own `build/`. That is outside `gitTracked()`, which is why a generated
 * file needs no `layout { }` declaration of its own.
 *
 * `WalkingSkeleton` stands where the processor's own name goes once there is one to put there.
 */
// TODO(v0.2 step 2): replace with `build/katachi/<Processor>/`, named from the registry key.
private const val SKELETON_OUTPUT_PATH: String = "build/katachi/WalkingSkeleton/skeleton.txt"

/**
 * The entry point the Gradle plugin's `runKatachiProcessor` task starts.
 *
 * The task is a `JavaExec` and not a `Test`, so no test engine is on the way in: the JVM is
 * started with the applying module's test runtime classpath and this function is the only thing
 * that runs. Its JVM name, `me.tbsten.katachi.processor.MainKt`, is written into the plugin as a
 * string, so moving this file breaks the task without breaking the build — a sample run is what
 * catches it.
 *
 * ## What it does today
 *
 * It writes one file and nothing else. Selecting processors, decoding their arguments and
 * reading the generated registry all arrive later; what this shape proves is that the plugin,
 * the user's classpath and this entry point are connected at all.
 *
 * An exception thrown here ends the JVM with a non-zero status, and `JavaExec` turns that into a
 * failed task. Nothing is caught on purpose: a run that fails silently would be worse than one
 * that never ran.
 *
 * ## Example 1: run the entry point the way the Gradle task does
 * ```kt
 * import me.tbsten.katachi.InternalKatachiApi
 * import me.tbsten.katachi.processor.main
 *
 * @OptIn(InternalKatachiApi::class)
 * fun runIt() {
 *     main(emptyArray())
 * }
 * ```
 *
 * @param args the command line the task passes on. Nothing reads it yet; it is written into the
 *   output so that a run can be told apart from the one before it.
 */
// TODO(v0.2 step 2): read `--processor` / `--arg` and drive the generated registry from here.
@InternalKatachiApi
public fun main(args: Array<String>) {
    val output = File(SKELETON_OUTPUT_PATH).absoluteFile
    output.parentFile?.mkdirs()
    output.writeText("katachi walking skeleton\nargs=${args.joinToString(separator = " ")}\n")
    println("[katachi] wrote $output")
}
