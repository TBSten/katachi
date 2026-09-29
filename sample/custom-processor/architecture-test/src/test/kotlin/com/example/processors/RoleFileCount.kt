@file:OptIn(ExperimentalKatachiApi::class)

package com.example.processors

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg

/**
 * How many files each role actually covers.
 *
 * The plainest of the three processors in this sample, and the one to copy first. It takes no
 * arguments, so it implements [ArchitectureProcessorNoArg] and never writes `argsSerializer`;
 * it holds no state, so it is an `object`.
 *
 * `context.roles` reads the declarations and touches nothing. `context.filesOf(role)` is the
 * other half: it walks the project. The walk happens on the first call and is reused by every
 * later one, so asking once per role still costs one walk.
 *
 * The result is a `List<String>` rather than one joined string because `katachiRoleFileCount`
 * prints a `Collection` one element per line; anything else arrives through `toString()`, which
 * for a list is a single bracketed line. It is always `Result.success`: counting has no answer
 * that would fail the run.
 *
 * ## Example 1: run it from a test, through the API
 * ```kt
 * val lines = projectArchitecture.process(RoleFileCount).getOrThrow()
 * lines shouldContain "core.Model: 1 file(s)"
 * ```
 *
 * @see RoleTable for the same shape with typed arguments.
 */
object RoleFileCount : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> = runCatching {
        context.log("Counting the files covered by ${context.roles.size} roles")
        context.roles.map { role ->
            "${role.qualifiedName}: ${context.filesOf(role).size} file(s)"
        }
    }
}
