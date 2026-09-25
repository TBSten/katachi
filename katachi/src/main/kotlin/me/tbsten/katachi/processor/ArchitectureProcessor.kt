package me.tbsten.katachi.processor

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Something that reads an architecture definition and answers with a [kotlin.Result] of [R].
 *
 * katachi had two fixed outputs -- the check, and documentation generation -- and no way for
 * anyone else to add a third. This is that way in: the check is one processor among others,
 * not a privileged one.
 *
 * It takes one parameter, [ArchitectureProcessContext], and nothing else. A later version that
 * has one more thing to hand a processor adds a member there, and every processor written
 * against this keeps compiling.
 *
 * A processor holds no state -- the definition arrives on the context -- so it can be written
 * as an `object`, and **there is nothing a user has to subclass.** A processor with settings is
 * still free to be a class.
 *
 * [R] may be [Unit]: a processor is allowed to have effects. It is deliberately not `suspend`,
 * which would force every plain JUnit or kotest test calling into it to wrap the call in
 * `runBlocking`.
 *
 * ## How [process] answers
 *
 * Write the body inside `runCatching { }`, and **when the answer is that the run does not pass,
 * throw inside it.** The exception's message is what a reader of the build log sees:
 * `runKatachiProcessor` reports `[FAILED]` and prints it, and reports `[OK]` with the value
 * otherwise.
 *
 * A check that reports `Violation`s says "I found something" by ending its body with
 * `me.tbsten.katachi.check.assertNoErrors`, which throws a `KatachiArchitectureAssertionError`
 * when there is an error among them. `validate()` and `assert()` put that exception's violations
 * into their one report; any other failure is read there as "this check could not do its job",
 * and becomes one `UncheckedCheck`. On the command line both are `[FAILED]`.
 *
 * The runner and `validate()` still catch a processor that throws out of `process` instead, and
 * treat it as the same failure, but a processor is expected not to.
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
 * ## Example 2: a check that makes the run fail when it found something
 * ```kt
 * import me.tbsten.katachi.processor.ArchitectureProcessContext
 * import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
 *
 * object ForbidTodoRoles : ArchitectureProcessorNoArg<List<String>> {
 *     override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> =
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
     * A processor author writes one `@Serializable data class` and gets every field typed,
     * instead of a getter per primitive type. A processor that takes no arguments does not
     * write this at all -- see [ArchitectureProcessorNoArg].
     *
     * ## Example 1: hand over the generated serializer
     * ```kt
     * object CountRoles : ArchitectureProcessor<CountRoles.Args, Int> {
     *     override val argsSerializer: KSerializer<Args> = Args.serializer()
     *
     *     override fun process(context: ArchitectureProcessContext<Args>): Result<Int> =
     *         runCatching { context.roles.count { it.name.startsWith(context.args.prefix) } }
     *
     *     @Serializable
     *     data class Args(val prefix: String = "")
     * }
     * ```
     */
    public val argsSerializer: KSerializer<Args>

    /**
     * Reads [context] and answers: `success` when the run passes, `failure` when it does not.
     * Write the body inside `runCatching { }` and throw inside it to fail -- see this interface's
     * own KDoc for how a check's findings are told apart from any other failure.
     *
     * ## Example 1: answer from the declarations alone
     * ```kt
     * object RoleCount : ArchitectureProcessorNoArg<Int> {
     *     override fun process(context: ArchitectureProcessContext<Unit>): Result<Int> =
     *         runCatching { context.roles.size }
     * }
     * ```
     */
    public fun process(context: ArchitectureProcessContext<Args>): Result<R>

    /**
     * The `--arg` names this one run may carry beyond the ones [argsSerializer] declares.
     *
     * A processor whose vocabulary depends on the definition rather than on its own type has no
     * field to declare those names as: a template's parameters differ from role to role, so
     * `Args` cannot name them and `checkNoUnknownArgs` would call every one of them a typo. This
     * is where such a processor says what this particular run made legal -- `--arg roleName=UseCase`
     * turns `name` and `implBody` into names, and nothing else into names.
     *
     * **Every selected processor is asked, with no build-script step in between.** A name this
     * answers with is accepted for the run; a name nothing answers with is still a typo.
     *
     * ## The contract
     *
     * - **Called before any processor of the run runs**, so [context] carries the run's raw
     *   arguments and the declarations, and **nothing here may read the file system.** An
     *   implementation that walks the project would make deciding "is this key a typo" cost a
     *   full scan.
     * - **Answer [emptySet] when there is nothing to say**, rather than throwing to mean it.
     *   Throwing is for the one case where nothing can be said because the run is already
     *   wrong -- a role name no role answers to, say. Then it ends the run, and it should:
     *   a processor that cannot tell a typo from a parameter has no answer to give, and
     *   "nothing" would be a wrong one stated as fact. The exception a build script sees is
     *   this one, so make it name what the user got wrong. This is called with no net for
     *   every selected processor, so whatever it throws ends the run.
     * - `context.args` is **not** this processor's `Args` yet -- decoding happens after this
     *   check. Read [ArchitectureProcessContext.rawArgs] instead.
     *
     * ## Example 1: take one argument per role the definition declares
     * ```kt
     * import me.tbsten.katachi.processor.ArchitectureProcessContext
     * import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
     *
     * object AnnotateRoles : ArchitectureProcessorNoArg<Unit> {
     *     override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> =
     *         runCatching { for ((role, note) in context.rawArgs) context.log("$role: $note") }
     *
     *     // `--arg domain/UseCase="owned by the platform team"`. The keys come from the
     *     // definition rather than from this processor, so no Args class can hold them as
     *     // fields -- which is the whole reason this method exists.
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
 * It carries the [argsSerializer] boilerplate and nothing else: `context.args` is still there
 * and is still [Unit]. Giving this interface a shorter `process()` of its own was considered
 * and dropped -- a second signature would thin out the one thing the context buys.
 *
 * This replaces v0.1's `ArchitectureProcessorUnit`, which was an alias for
 * `ArchitectureProcessor<Unit>`. With two type parameters that alias could no longer say what
 * it meant, so the shape has a name instead.
 *
 * ## Example 1: a processor that exists for its effect
 * ```kt
 * import java.io.File
 * import me.tbsten.katachi.processor.ArchitectureProcessContext
 * import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
 * import me.tbsten.katachi.processor.process
 *
 * class RoleSummaryReport(
 *     private val write: (path: String, content: String) -> Unit,
 * ) : ArchitectureProcessorNoArg<Unit> {
 *     override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> = runCatching {
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
}
