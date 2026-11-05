package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role

/**
 * What a definition declares, and the `--arg` values of the run: everything a processor can read without touching the file system.
 *
 * This is the whole of what [ArchitectureProcessor.undeclaredArgNames] is handed. It is asked
 * before any processor runs, so a walk of the project would be paid for by every run that
 * follows; keeping [ArchitectureProcessContext.filesOf] off this type means the file system is
 * out of reach there by construction rather than by convention.
 *
 * [ArchitectureProcessContext] extends this with the decoded arguments, the files, and a log.
 *
 * ## Example 1: name one argument per role, from the declarations alone
 * ```kt
 * object AnnotateRoles : ArchitectureProcessorNoArg<Unit> {
 *     override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> =
 *         runCatching { for ((role, note) in context.rawArgs) context.log("$role: $note") }
 *
 *     // --arg domain.UseCase="owned by the platform team"
 *     override fun undeclaredArgNames(context: ArchitectureDeclarationContext): Set<String> =
 *         context.roles.map { it.qualifiedName }.toSet()
 * }
 * ```
 *
 * @see ArchitectureProcessContext
 */
@ExperimentalKatachiApi
public interface ArchitectureDeclarationContext {
    /** The definition being processed. */
    public val architecture: Architecture

    /**
     * Every `--arg` of this run, by name, exactly as the command line spelled it.
     *
     * A processor normally reads [args]. This undecoded map is for names no fixed set of fields
     * could declare, such as the parameters a `.template { }` declares, which differ per role.
     *
     * Empty for a run started in code with `Architecture.process`.
     *
     * ## Example 1: read a value no Args field could declare
     * ```kt
     * object PrintRawArgs : ArchitectureProcessorNoArg<Unit> {
     *     override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching {
     *         for ((name, value) in context.rawArgs) context.log("$name=$value")
     *     }
     * }
     * ```
     */
    public val rawArgs: Map<String, String>

    /**
     * Every group, nested ones included, parents before their children, in declaration order.
     *
     * [Group.groups] holds the children, and [Group.path] says where a group sits.
     */
    public val groups: List<Group>

    /**
     * Every role, those declared at the root of `architecture { }` included, in declaration order.
     *
     * A role that sits in no group has an empty [Role.groupPath], and its [Role.qualifiedName] is its
     * own name. To turn a role into a directory, join [Role.groupPath] with `/`; [Role.qualifiedName]
     * is `.` separated and meant for reading and comparing.
     */
    public val roles: List<Role>

    /**
     * Every `layout { }` block of every role, flattened into one list of path patterns.
     *
     * The declared shape of the project, not its contents: a pattern is here whether or not a file
     * matches it, so reading it needs no IO. A pattern holding a wildcard stays one pattern here,
     * however many files or modules it would match; [ArchitectureProcessContext.filesOf] is the one that looks.
     */
    public val declaredEntries: List<LayoutEntry>
}
