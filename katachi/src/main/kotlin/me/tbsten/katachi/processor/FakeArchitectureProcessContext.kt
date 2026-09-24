package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.fs.KatachiFileSystem

/**
 * A context that answers exactly as the real one does and remembers what was logged.
 *
 * It delegates to the real implementation rather than reimplementing it, so a spec written
 * against this is a spec about the real walk: the only thing that differs is where
 * [ArchitectureProcessContext.log] goes.
 *
 * ## Example 1: check what a processor reported, without capturing standard output
 * ```kt
 * @OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
 * class GenerateDocumentationSpec : FreeSpec({
 *     "walks the roles and says so" {
 *         val context = FakeArchitectureProcessContext(
 *             architecture = architecture { "domain".group { "UseCase" { } } },
 *             args = Unit,
 *             fileSystem = RealFileSystem(),
 *         )
 *
 *         GenerateDocumentation.process(context)
 *
 *         context.logs shouldBe listOf("Scanning 1 roles...")
 *     }
 * })
 * ```
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public class FakeArchitectureProcessContext<Args>(
    architecture: Architecture,
    args: Args,
    fileSystem: KatachiFileSystem,
) : ArchitectureProcessContext<Args> {
    private val recorded: MutableList<String> = mutableListOf()

    internal val real: RealArchitectureProcessContext<Args> =
        RealArchitectureProcessContext(architecture, args, fileSystem, onLog = { recorded += it })

    /**
     * What [ArchitectureProcessContext.log] was called with, in order.
     *
     * ## Example 1: assert on the messages a processor produced
     * ```kt
     * context.logs shouldContain "Scanning architecture..."
     * ```
     */
    public val logs: List<String> get() = recorded.toList()

    override val architecture: Architecture get() = real.architecture
    override val args: Args get() = real.args
    override val fileSystem: KatachiFileSystem get() = real.fileSystem
    override val groups: List<Group> get() = real.groups
    override val roles: List<Role> get() = real.roles
    override val declaredEntries: List<LayoutEntry> get() = real.declaredEntries

    override fun filesOf(role: Role): List<String> = real.filesOf(role)

    override fun log(message: String): Unit = real.log(message)

    override fun <A> withArgs(
        args: A,
        onLog: ((String) -> Unit)?,
    ): ArchitectureProcessContext<A> = real.withArgs(args, onLog)

    override fun toString(): String =
        "FakeArchitectureProcessContext(args=$args, logs=${recorded.size})"
}
