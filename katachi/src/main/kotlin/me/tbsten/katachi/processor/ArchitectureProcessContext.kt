package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Role

/**
 * Everything an [ArchitectureProcessor] is handed: the declarations, its arguments, and on request the matching files.
 *
 * **Nothing here touches the file system until [filesOf] is called**, the search for the
 * project root included. A processor that only reads declarations therefore runs with no IO, so
 * it can run inside a unit test, or against a definition whose project is not checked out.
 *
 * [declaredEntries] is what the definition *says*; [filesOf] is what the project *has*. The
 * declarations alone are [ArchitectureDeclarationContext], which is all [ArchitectureProcessor.undeclaredArgNames]
 * is handed.
 *
 * ## Example 1: read the declarations, the arguments and the files
 * ```kt
 * object RoleFiles : ArchitectureProcessor<RoleFiles.Args, Map<String, List<String>>> {
 *     override val argsSerializer: KSerializer<Args> = Args.serializer()
 *
 *     override fun process(context: ArchitectureProcessContext<Args>) = runCatching {
 *         context.log("Scanning ${context.roles.size} roles in ${context.groups.size} groups")
 *         context.roles
 *             .filter { it.name.startsWith(context.args.prefix) }
 *             .associate { it.qualifiedName to context.filesOf(it) }
 *     }
 *
 *     @Serializable
 *     data class Args(val prefix: String = "")
 * }
 *
 * projectArchitecture.process(RoleFiles, RoleFiles.Args("Get")).getOrThrow()
 * ```
 *
 * ## Example 2: a processor that reads the declarations and nothing else
 * ```kt
 * val arch = architecture {
 *     "domain".group { "UseCase".role { layout { "useCase" / "GetUserUseCase".ktFile() } } }
 * }
 * arch.process { context -> context.declaredEntries.map { it.path } } shouldContainExactly
 *     listOf("useCase", "useCase/GetUserUseCase.kt")
 * ```
 *
 * @see ArchitectureProcessor
 */
@ExperimentalKatachiApi
public interface ArchitectureProcessContext<out Args> : ArchitectureDeclarationContext {
    /**
     * The arguments this run was started with, decoded into the processor's own type.
     *
     * `Unit` for a processor that takes none -- see [ArchitectureProcessorNoArg].
     */
    public val args: Args

    /**
     * Files that exist and that [role]'s `layout { }` allows, as project relative `/` separated paths, in walk order.
     *
     * **All of them, overlaps included.** Two roles may both allow one file, and the check accepts
     * that, so this does not pick a single owner. A file under an `ignore()` is never here.
     *
     * The first call finds the project root and walks the tree; later calls, for any role, reuse
     * that walk.
     *
     * @throws KatachiUnknownRoleException when [role] was declared in some other definition.
     *   Checked before the walk starts.
     */
    public fun filesOf(role: Role): List<String>

    /**
     * Reports progress to wherever the caller of the processor sends messages, rather than to standard output.
     *
     * Unlike `println`, this can be checked in a test without capturing the JVM's output stream.
     */
    public fun log(message: String)
}
