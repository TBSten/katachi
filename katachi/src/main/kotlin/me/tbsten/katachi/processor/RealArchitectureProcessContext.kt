package me.tbsten.katachi.processor

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.fs.KatachiFileSystem

/**
 * The context every `process` entry point builds.
 *
 * `withArgs` passes [walk] along by reference rather than rebuilding it, which is what keeps
 * one run at one walk of the project and one set of evaluated constraints.
 */
internal class RealArchitectureProcessContext<Args>(
    internal val walk: ProjectWalk,
    override val args: Args,
    private val onLog: (String) -> Unit,
    // Last and defaulted so that `withArgs` and every `Architecture.process` overload keep their
    // call shape: a run started in code has no command line to take one from.
    override val rawArgs: Map<String, String> = emptyMap(),
) : ArchitectureProcessContext<Args> {
    constructor(
        architecture: Architecture,
        args: Args,
        fileSystem: KatachiFileSystem,
        onLog: (String) -> Unit = {},
        rawArgs: Map<String, String> = emptyMap(),
    ) : this(ProjectWalk(architecture, fileSystem), args, onLog, rawArgs)

    override val architecture: Architecture get() = walk.architecture
    override val fileSystem: KatachiFileSystem get() = walk.fileSystem
    override val groups: List<Group> get() = walk.groups
    override val roles: List<Role> get() = walk.roles
    override val declaredEntries: List<LayoutEntry> get() = walk.declaredEntries

    override fun filesOf(role: Role): List<String> = walk.filesOf(role)

    override fun log(message: String): Unit = onLog(message)

    override fun <A> withArgs(
        args: A,
        onLog: ((String) -> Unit)?,
    ): ArchitectureProcessContext<A> =
        RealArchitectureProcessContext(walk, args, onLog ?: this.onLog, rawArgs)

    override fun toString(): String = "ArchitectureProcessContext(args=$args, $walk)"
}

/**
 * The walk behind [this], for katachi's own checks.
 *
 * [me.tbsten.katachi.check.LayoutCheck] and [me.tbsten.katachi.check.KonsistCheck] read
 * violations, constraints and the project root, none of which is on the public
 * [ArchitectureProcessContext]: a processor does not read violations off the context, it *is* a
 * check and produces them. This door exists only so that katachi's own checks can be processors
 * among others instead of a second walk of the tree.
 */
internal val ArchitectureProcessContext<*>.projectWalk: ProjectWalk
    get() = when (this) {
        is RealArchitectureProcessContext<*> -> walk
        is FakeArchitectureProcessContext<*> -> real.walk
        else -> throw KatachiForeignProcessContextException(this::class.java.name)
    }
