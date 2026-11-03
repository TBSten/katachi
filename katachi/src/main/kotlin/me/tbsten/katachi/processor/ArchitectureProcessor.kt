package me.tbsten.katachi.processor

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.internal.NoArgContextView

/**
 * Something that reads an architecture definition and answers with a [kotlin.Result] of [R].
 *
 * The layout check, documentation generation and template generation are all processors; this
 * is how you add your own. A processor holds no state -- the definition arrives on
 * [ArchitectureProcessContext] -- so it can be written as an `object`. [R] may be [Unit] for a
 * processor that exists for its effect.
 *
 * ## How [process] answers
 *
 * Write the body inside `runCatching { }`, and **throw inside it when the run does not pass.**
 * The exception's message is what the build log shows: a `katachi<Key>` task reports `[FAILED]`
 * with it, and `[OK]` with the value otherwise.
 *
 * A check that reports `Violation`s ends its body with `me.tbsten.katachi.check.assertNoErrors`,
 * which throws a `KatachiArchitectureAssertionError` when there is an error among them.
 * `assert()` and `assertNoErrors()` report those violations; any other failure is reported as
 * one `UncheckedCheck`.
 *
 * ## Example 1: a processor with typed arguments, written as an object
 * ```kt
 * import kotlinx.serialization.KSerializer
 * import kotlinx.serialization.Serializable
 * import me.tbsten.katachi.processor.ArchitectureProcessContext
 * import me.tbsten.katachi.processor.ArchitectureProcessor
 * import me.tbsten.katachi.processor.process
 *
 * object CountRoles : ArchitectureProcessor<CountRoles.Args, Int> {
 *     override val argsSerializer: KSerializer<Args> = Args.serializer()
 *
 *     override fun process(context: ArchitectureProcessContext<Args>): Result<Int> = runCatching {
 *         context.log("Counting roles with prefix=${context.args.prefix}")
 *         context.roles.count { it.name.startsWith(context.args.prefix) }
 *     }
 *
 *     @Serializable
 *     data class Args(val prefix: String = "")
 * }
 *
 * val count: Int = projectArchitecture.process(CountRoles, CountRoles.Args("Get")).getOrThrow()
 * ```
 *
 * ## Example 2: register it and run it from the command line
 * ```kts
 * // architecture-test/build.gradle.kts
 * katachi {
 *     processors {
 *         register("countRoles", "com.example.processors.CountRoles")
 *     }
 * }
 * ```
 *
 * ```sh
 * ./gradlew :architecture-test:katachiCountRoles --arg prefix=Get
 * ```
 *
 * ## Example 3: a check that makes the run fail when it found something
 * ```kt
 * import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
 * import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
 *
 * object ForbidTodoRoles : ArchitectureProcessorNoArg<List<String>> {
 *     override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> =
 *         runCatching {
 *             val todo = context.roles.map { it.qualifiedName }.filter { it.startsWith("Todo") }
 *             check(todo.isEmpty()) { "Roles still named Todo: $todo" }
 *             todo
 *         }
 * }
 * ```
 */
@ExperimentalKatachiApi
public interface ArchitectureProcessor<Args, R> {
    /**
     * How a `--arg key=value` map becomes [Args].
     *
     * Write one `@Serializable data class` and hand over its serializer. A processor that takes no
     * arguments does not write this at all -- see [ArchitectureProcessorNoArg].
     */
    public val argsSerializer: KSerializer<Args>

    /**
     * Reads [context] and answers: `success` when the run passes, `failure` when it does not.
     *
     * Write the body inside `runCatching { }` and throw inside it to fail.
     */
    public fun process(context: ArchitectureProcessContext<Args>): Result<R>

    /**
     * The `--arg` names this run may carry beyond the ones [argsSerializer] declares.
     *
     * For a processor whose arguments depend on the definition rather than on its own type. A
     * template's parameters differ from role to role, so `--arg template=UseCase` makes `name` and
     * `implBody` valid names for that run. A name no selected processor accepts is reported as a typo.
     *
     * ## The contract
     *
     * - Called before any processor of the run runs. **Do not read the file system here**: read
     *   the declarations and [ArchitectureProcessContext.rawArgs].
     * - `context.args` is not decoded yet. Read [ArchitectureProcessContext.rawArgs] instead.
     * - Answer [emptySet] when there is nothing to add. Throw only when the run is already wrong,
     *   such as a role name no role answers to; the exception ends the run, so make its message
     *   name what the user got wrong.
     *
     * ## Example 1: take one argument per role the definition declares
     * ```kt
     * object AnnotateRoles : ArchitectureProcessorNoArg<Unit> {
     *     override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> =
     *         runCatching { for ((role, note) in context.rawArgs) context.log("$role: $note") }
     *
     *     // --arg domain.UseCase="owned by the platform team"
     *     override fun undeclaredArgNames(context: ArchitectureProcessContext<*>): Set<String> =
     *         context.roles.map { it.qualifiedName }.toSet()
     * }
     * ```
     */
    public fun undeclaredArgNames(context: ArchitectureProcessContext<*>): Set<String> = emptySet()
}

/**
 * An [ArchitectureProcessor] that takes no arguments.
 *
 * It needs no [argsSerializer], and its [process] receives an [ArchitectureProcessNoArgContext],
 * which has no `args` to read.
 *
 * ## Example 1: a processor that exists for its effect
 * ```kt
 * import java.io.File
 * import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
 * import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
 * import me.tbsten.katachi.processor.process
 *
 * class RoleSummaryReport(
 *     private val write: (path: String, content: String) -> Unit,
 * ) : ArchitectureProcessorNoArg<Unit> {
 *     override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching {
 *         for (role in context.roles) write("${role.qualifiedName}.md", "# ${role.qualifiedName}\n")
 *     }
 * }
 *
 * projectArchitecture.process(RoleSummaryReport { path, text -> File(path).writeText(text) })
 *     .getOrThrow()
 * ```
 */
@ExperimentalKatachiApi
public interface ArchitectureProcessorNoArg<R> : ArchitectureProcessor<Unit, R> {
    override val argsSerializer: KSerializer<Unit> get() = Unit.serializer()
    override fun process(context: ArchitectureProcessContext<Unit>): Result<R> =
        this.process(context as? ArchitectureProcessNoArgContext ?: NoArgContextView(context))

    /** Reads [context] and answers, exactly as [ArchitectureProcessor.process] does. */
    public fun process(context: ArchitectureProcessNoArgContext): Result<R>
}

/**
 * The context an [ArchitectureProcessorNoArg] reads: an [ArchitectureProcessContext] with no `args` to read.
 *
 * ## Example 1: read the declarations in a processor without arguments
 * ```kt
 * object RoleNames : ArchitectureProcessorNoArg<List<String>> {
 *     override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> =
 *         runCatching { context.roles.map { it.qualifiedName } }
 * }
 * ```
 */
@ExperimentalKatachiApi
public interface ArchitectureProcessNoArgContext : ArchitectureProcessContext<Unit> {
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("In the NoArg Processor, no meaningful value is assigned to `args`. Please use ArchitectureProcessor if an argument is required.")
    override val args: Unit get() = Unit
}
