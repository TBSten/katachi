package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.KatachiForeignProcessContextException

internal class RealArchitectureProcessContext<Args>(
    internal val walk: ProjectWalk,
    override val args: Args,
    // Internal rather than private so that the top-level `withArgs` below -- the only place that
    // ever needs to carry it forward -- can read it back out of a context it did not build.
    internal val onLog: (String) -> Unit,
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
    override val groups: List<Group> get() = walk.groups
    override val roles: List<Role> get() = walk.roles
    override val declaredEntries: List<LayoutEntry> get() = walk.declaredEntries

    override fun filesOf(role: Role): List<String> = walk.filesOf(role)

    override fun log(message: String): Unit = onLog(message)

    override fun toString(): String = "ArchitectureProcessContext(args=$args, $walk)"
}

internal val ArchitectureProcessContext<*>.projectWalk: ProjectWalk
    get() = when (this) {
        is RealArchitectureProcessContext<*> -> walk
        is FakeArchitectureProcessContext<*> -> real.walk
        is NoArgContextView -> base.projectWalk
        else -> throw KatachiForeignProcessContextException(this::class.java.name)
    }

internal val ArchitectureProcessContext<*>.fileSystem: KatachiFileSystem get() = projectWalk.fileSystem

internal fun <Args, A> ArchitectureProcessContext<Args>.withArgs(
    args: A,
    onLog: ((String) -> Unit)? = null,
): ArchitectureProcessContext<A> = when (this) {
    is RealArchitectureProcessContext<*> -> RealArchitectureProcessContext(walk, args, onLog ?: this.onLog, rawArgs)
    is FakeArchitectureProcessContext<*> -> real.let {
        RealArchitectureProcessContext(it.walk, args, onLog ?: it.onLog, it.rawArgs)
    }
    is NoArgContextView -> base.withArgs(args, onLog)
    else -> throw KatachiForeignProcessContextException(this::class.java.name)
}
