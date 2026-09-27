package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role

/**
 * Everything an [ArchitectureProcessor] is handed: the declarations, the arguments it was
 * started with, and -- on request -- the files that turned out to match those declarations.
 *
 * `process()` takes this and nothing else. A processor that later needs one more thing gets it
 * as a member here, and every processor already written keeps compiling: the signature a
 * library publishes once and can never reshape is the one worth keeping small.
 *
 * **Nothing here touches the file system until [filesOf] is called**, the search for the
 * project root included. A processor that only reads declarations therefore runs with no IO at
 * all, which is the point: it can run inside an `architecture { }` unit test, or against a
 * definition whose project is not checked out.
 *
 * That is also why [declaredEntries] and [filesOf] are named apart. [declaredEntries] is what
 * the definition *says*, read without looking at anything; [filesOf] is what the project
 * *has*. A wildcard module key is where the difference shows -- see [declaredEntries].
 *
 * ## Example 1: a processor that reads the declarations and nothing else
 * ```kt
 * object RoleNames : ArchitectureProcessorNoArg<List<String>> {
 *     override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> =
 *         runCatching { context.roles.map { it.qualifiedName } }
 * }
 *
 * projectArchitecture.process(RoleNames).getOrThrow() shouldContain "domain/UseCase"
 * ```
 *
 * @see ArchitectureProcessor
 */
@ExperimentalKatachiApi
public interface ArchitectureProcessContext<out Args> {
    /**
     * The definition being processed.
     *
     * It is here rather than on the processor so that a processor holds no state of its own
     * and can be written as an `object`. An architecture is a property of the project, not of
     * the processor.
     *
     * ## Example 1: reach the definition itself rather than the flattened views
     * ```kt
     * object TopLevelGroupCount : ArchitectureProcessorNoArg<Int> {
     *     override fun process(context: ArchitectureProcessNoArgContext): Result<Int> =
     *         runCatching { context.architecture.allGroups.count { it.path.isEmpty() } }
     * }
     * ```
     */
    public val architecture: Architecture

    /**
     * The arguments this run was started with, decoded into the processor's own type.
     *
     * `Unit` for a processor that takes none -- see [ArchitectureProcessorNoArg].
     *
     * ## Example 1: read a typed argument
     * ```kt
     * object GenerateOne : ArchitectureProcessor<GenerateOne.Args, Unit> {
     *     override val argsSerializer: KSerializer<Args> = Args.serializer()
     *
     *     override fun process(context: ArchitectureProcessContext<Args>) {
     *         context.log("generating for ${context.args.roleName}")
     *     }
     *
     *     @Serializable
     *     data class Args(val roleName: String)
     * }
     * ```
     */
    public val args: Args

    /**
     * Every `--arg` of this run, by name, exactly as the command line spelled it.
     *
     * [args] is the typed reading of these, narrowed to the fields one processor declares, and is
     * what a processor normally works with. This is the undecoded map, and it exists for the one
     * shape [args] cannot have: a vocabulary that differs per role, such as the parameters a
     * `template { }` declares, which no fixed set of fields could name.
     *
     * **Being able to read a name is not the same as being allowed to be passed it.** Whether a
     * `--arg` belongs to this run at all is decided once, before any processor starts; what a
     * processor may *see* afterwards is simply everything, because hiding half a map would buy
     * nothing a reader could rely on.
     *
     * Empty for a run started in code rather than from the command line -- `Architecture.process`
     * has nowhere to take one from.
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
     * Every group, parents before their children, in declaration order.
     *
     * Flat rather than only the top level ones, so that a processor asking "which groups carry
     * this metadata" does not have to write the recursion itself. The tree is still there:
     * [Group.groups] holds the children, and [Group.path] says where a group sits.
     *
     * ## Example 1: list every group, nested ones included
     * ```kt
     * val arch = architecture {
     *     "domain".group { "model".group { "Entity" { } } }
     * }
     * arch.process { context -> context.groups.map { it.qualifiedName } } shouldContainExactly
     *     listOf("domain", "domain/model")
     * ```
     */
    public val groups: List<Group>

    /**
     * Every role, those declared at the root of `architecture { }` included, in declaration
     * order.
     *
     * A role that sits in no group carries an empty [Role.groupPath], and its
     * [Role.qualifiedName] is its own name. A processor that turns the path into a directory
     * therefore writes such a role at the top of its output rather than below a group.
     *
     * ## Example 1: pick the roles a processor cares about
     * ```kt
     * val arch = architecture {
     *     "domain".group { "UseCase" { } }
     *     "data".group { "Repository" { } }
     * }
     * arch.process { context -> context.roles.map { it.qualifiedName } } shouldContainExactly
     *     listOf("domain/UseCase", "data/Repository")
     * ```
     *
     * ## Example 2: a role declared at the root is one of them
     * ```kt
     * val arch = architecture {
     *     "Readme" { }
     *     "domain".group { "UseCase" { } }
     * }
     * arch.process { context -> context.roles.map { it.qualifiedName } } shouldContainExactly
     *     listOf("Readme", "domain/UseCase")
     * ```
     */
    public val roles: List<Role>

    /**
     * Every `layout { }` block of every role, flattened into one list of path patterns.
     *
     * The declared shape of the project, not its actual contents: a pattern is here whether or
     * not a file matches it, which is what makes it readable without IO. `declared` is in the
     * name because that limit is the whole of what this is, and because the alternative
     * reading -- "the entries of this project" -- is the one that would be wrong.
     *
     * A layout key naming modules with a wildcard (`":feature:*"`) is where the two readings
     * come apart. It stands for the modules that exist, and which modules exist is a question
     * only the file system can answer -- so here, where nothing is read, such a key stays
     * **one pattern**: it contributes one module's worth of entries whose paths still hold the
     * wildcard, never as many as the project happens to have feature modules. [filesOf] --
     * which does walk the project -- sees that same key once per module that exists.
     *
     * ## Example 1: read the declared paths
     * ```kt
     * val arch = architecture {
     *     "domain".group { "UseCase" { layout { "useCase" / "GetUserUseCase".ktFile() } } }
     * }
     * arch.process { context -> context.declaredEntries.map { it.path } } shouldContainExactly
     *     listOf("useCase", "useCase/GetUserUseCase.kt")
     * ```
     *
     * ## Example 2: a wildcard module key is declared here as one pattern
     * ```kt
     * val arch = architecture {
     *     "feature".group { "Module" { layout { ":feature:*".module { } } } }
     * }
     * // Four entries however many feature modules the project holds, with the wildcard still
     * // standing where a module name would be. Split into levels only so that this example
     * // can be written inside a KDoc comment at all.
     * arch.process { context ->
     *     context.declaredEntries.map { it.path.split('/') }
     * } shouldContainExactly listOf(
     *     listOf("feature"),
     *     listOf("feature", "*"),
     *     listOf("feature", "*", "build"),
     *     listOf("feature", "*", "build.gradle.kts"),
     * )
     * ```
     */
    public val declaredEntries: List<LayoutEntry>

    /**
     * Files that exist and that [role]'s `layout { }` allows, as project relative `/`
     * separated paths, in walk order.
     *
     * **All of them, overlaps included.** Two roles may claim patterns that both match one
     * file, and the check deliberately allows that -- a file is fine as long as *some* role
     * allows it. Answering with a single "real" owner would be inventing a rule the check does
     * not have.
     *
     * Only files the walk reached are here. A declaration nothing matches contributes no path,
     * and a file under an `ignore()` is never looked at in the first place.
     *
     * The first call resolves the project root and walks the tree; later calls, for this role
     * or any other, reuse that one walk -- including composition's `+` and a CLI run naming
     * several processors, both of which share one walk internally.
     *
     * ## Example 1: collect the files of the roles a processor cares about
     * ```kt
     * val platformFiles = projectArchitecture.process { context ->
     *     context.roles.filter { it.name.startsWith("Gradle") }.flatMap { context.filesOf(it) }
     * }
     * platformFiles shouldContain "settings.gradle.kts"
     * ```
     *
     * @throws KatachiUnknownRoleException when [role] was declared in some other definition.
     *   An empty list would say "this role owns nothing", which is a different statement and
     *   one nothing could catch. Checked before the walk starts, so a mistake here costs no IO.
     */
    public fun filesOf(role: Role): List<String>

    /**
     * Reports progress, through whatever the caller wired up rather than standard output.
     *
     * A processor that writes with `println` cannot be tested without capturing the JVM's
     * output stream. This one can: whoever runs the processor decides where the messages go.
     *
     * ## Example 1: say what is happening, testably
     * ```kt
     * object GenerateDocumentation : ArchitectureProcessorNoArg<Unit> {
     *     override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching {
     *         context.log("Scanning ${context.roles.size} roles...")
     *     }
     * }
     * ```
     */
    public fun log(message: String)
}
