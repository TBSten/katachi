package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessContext

internal class FakeArchitectureProcessContext<Args>(
    architecture: Architecture,
    args: Args,
    fileSystem: KatachiFileSystem,
    rawArgs: Map<String, String> = emptyMap(),
) : ArchitectureProcessContext<Args> {
    private val recorded: MutableList<String> = mutableListOf()

    internal val real: RealArchitectureProcessContext<Args> = RealArchitectureProcessContext(
        architecture = architecture,
        args = args,
        fileSystem = fileSystem,
        onLog = { recorded += it },
        rawArgs = rawArgs,
    )

    val logs: List<String> get() = recorded.toList()

    override val architecture: Architecture get() = real.architecture
    override val args: Args get() = real.args
    override val rawArgs: Map<String, String> get() = real.rawArgs
    override val groups: List<Group> get() = real.groups
    override val roles: List<Role> get() = real.roles
    override val declaredEntries: List<LayoutEntry> get() = real.declaredEntries

    override fun filesOf(role: Role): List<String> = real.filesOf(role)

    override fun log(message: String): Unit = real.log(message)

    override fun toString(): String =
        "FakeArchitectureProcessContext(args=$args, logs=${recorded.size})"
}
