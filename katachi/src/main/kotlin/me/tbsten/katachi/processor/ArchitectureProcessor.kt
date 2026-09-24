package me.tbsten.katachi.processor

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Something that reads an architecture definition and produces [Result].
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
 * [Result] may be [Unit]: a processor is allowed to have effects. It is deliberately not
 * `suspend`, which would force every plain JUnit or kotest test calling into it to wrap the
 * call in `runBlocking`.
 *
 * ## Example 1: a processor with typed arguments, written as an object
 * ```kt
 * object GenerateCodeFromTemplate : ArchitectureProcessor<GenerateCodeFromTemplate.Args, Unit> {
 *     override val argsSerializer: KSerializer<Args> = Args.serializer()
 *
 *     override fun process(context: ArchitectureProcessContext<Args>) {
 *         context.log("Generating for role=${context.args.roleName}")
 *     }
 *
 *     @Serializable
 *     data class Args(val roleName: String)
 * }
 *
 * projectArchitecture.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args("GetUser"))
 * ```
 */
@ExperimentalKatachiApi
public interface ArchitectureProcessor<Args, Result> {
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
     *     override fun process(context: ArchitectureProcessContext<Args>): Int =
     *         context.roles.count { it.name.startsWith(context.args.prefix) }
     *
     *     @Serializable
     *     data class Args(val prefix: String = "")
     * }
     * ```
     */
    public val argsSerializer: KSerializer<Args>

    /**
     * Reads [context] and produces this processor's result.
     *
     * ## Example 1: return something derived from the declarations alone
     * ```kt
     * object RoleCount : ArchitectureProcessorNoArg<Int> {
     *     override fun process(context: ArchitectureProcessContext<Unit>): Int =
     *         context.roles.size
     * }
     * ```
     */
    public fun process(context: ArchitectureProcessContext<Args>): Result
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
 * class RoleSummaryReport(
 *     private val write: (path: String, content: String) -> Unit,
 * ) : ArchitectureProcessorNoArg<Unit> {
 *     override fun process(context: ArchitectureProcessContext<Unit>) {
 *         for (role in context.roles) write("${role.qualifiedName}.md", "# ${role.qualifiedName}\n")
 *     }
 * }
 *
 * projectArchitecture.process(RoleSummaryReport { path, text -> File(path).writeText(text) })
 * ```
 *
 * ## Example 2: the same processor, verifying instead of writing
 * ```kt
 * val stale = mutableListOf<String>()
 * projectArchitecture.process(
 *     RoleSummaryReport { path, text -> if (File(path).readText() != text) stale += path },
 * )
 * stale.shouldBeEmpty()
 * ```
 */
@ExperimentalKatachiApi
public interface ArchitectureProcessorNoArg<Result> : ArchitectureProcessor<Unit, Result> {
    override val argsSerializer: KSerializer<Unit> get() = Unit.serializer()
}
