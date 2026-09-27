package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessContext

/**
 * A context that answers exactly as the real one does and remembers what was logged.
 *
 * It delegates to the real implementation rather than reimplementing it, so a spec written
 * against this is a spec about the real walk: the only thing that differs is where
 * [ArchitectureProcessContext.log] goes. Meant for katachi's own processor specs: run it against an
 * in-memory [architecture] and read [logs] back, without capturing standard output.
 *
 * ## Example 1: check what a processor reported, without capturing standard output
 * ```kt
 * @OptIn(ExperimentalKatachiApi::class)
 * class GenerateDocumentationSpec : FreeSpec({
 *     "walks the roles and says so" {
 *         val context = FakeArchitectureProcessContext(
 *             architecture = architecture { "domain".group { "UseCase" { } } },
 *             args = GenerateDocumentation.Args(outputDir = "build/katachi/docs"),
 *             fileSystem = RealFileSystem(),
 *         )
 *
 *         GenerateDocumentation.process(context).getOrThrow()
 *
 *         context.logs.first() shouldStartWith "Writing "
 *     }
 * })
 * ```
 */
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

    /**
     * What [ArchitectureProcessContext.log] was called with, in order.
     *
     * ## Example 1: read back what a processor logged
     * ```kt
     * @OptIn(ExperimentalKatachiApi::class)
     * class GenerateDocumentationSpec : FreeSpec({
     *     "walks the roles and says so" {
     *         val context = FakeArchitectureProcessContext(
     *             architecture = architecture { "domain".group { "UseCase" { } } },
     *             args = GenerateDocumentation.Args(outputDir = "build/katachi/docs"),
     *             fileSystem = RealFileSystem(),
     *         )
     *
     *         GenerateDocumentation.process(context).getOrThrow()
     *
     *         context.logs.first() shouldStartWith "Writing "
     *     }
     * })
     * ```
     */
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
