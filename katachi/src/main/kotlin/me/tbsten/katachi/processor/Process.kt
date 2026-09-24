package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.RealFileSystem

/**
 * Runs [processor] against this definition and the real project, and returns its result.
 *
 * Reading the project is deferred to [ArchitectureProcessContext.filesOf], so a processor that
 * only looks at declarations does no IO -- the search for the project root included.
 *
 * ## Example 1: run a processor that takes no arguments
 * ```kt
 * val violations = projectArchitecture.process(LayoutCheck())
 * violations.map { it.path } shouldContain "notes.md"
 * ```
 *
 * @throws me.tbsten.katachi.fs.KatachiProjectRootNotFoundException when the processor asks for
 *   files and no directory above the working directory carries a Gradle, Maven or git marker.
 */
@ExperimentalKatachiApi
public fun <R> Architecture.process(processor: ArchitectureProcessor<Unit, R>): R =
    process(processor, Unit, RealFileSystem())

/**
 * [process] for a processor that takes arguments, with the arguments built in code rather than
 * decoded from a `--arg` line.
 *
 * ## Example 1: run a processor with its own Args type
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
 *
 * val arch = architecture {
 *     "domain".group { "GetUserUseCase" { }; "GetOrderUseCase" { } }
 *     "data".group { "GetUserRepository" { } }
 * }
 * val count = arch.process(CountRoles, CountRoles.Args(prefix = "Get"))
 * count shouldBe 3
 * ```
 */
@ExperimentalKatachiApi
public fun <Args, R> Architecture.process(
    processor: ArchitectureProcessor<Args, R>,
    args: Args,
): R = process(processor, args, RealFileSystem())

/**
 * [process] with the processor written inline, for something used once.
 *
 * ## Example 1: pull out the files of the roles you care about
 * ```kt
 * val gradleFiles = projectArchitecture.process { context ->
 *     context.roles.filter { it.name.startsWith("Gradle") }.flatMap { context.filesOf(it) }
 * }
 * gradleFiles shouldContain "settings.gradle.kts"
 * ```
 */
@ExperimentalKatachiApi
public fun <R> Architecture.process(block: (ArchitectureProcessContext<Unit>) -> R): R =
    process(RealFileSystem(), block)

/**
 * [process] against [fileSystem], which is how katachi's own specs run a processor against a
 * tree that only exists in memory. Same deferral: [fileSystem] is untouched unless the
 * processor asks for files.
 *
 * `@InternalKatachiApi`, so this door is katachi's own: [KatachiFileSystem] is marked the same
 * way, so a processor written outside `:katachi` tests against a real checkout instead.
 *
 * ## Example 1: run a processor against a file system built for one spec
 * ```kt
 * val arch = architecture {
 *     "domain".group { "UseCase" { layout { "domain" / "*UseCase.kt".file() } } }
 * }
 * val fileSystem = object : KatachiFileSystem {
 *     override val workingDirectory: FsPath = FsPath.of("/repo")
 *     // "gradlew" is one of the project root markers LayoutCheck's walk looks for; without one
 *     // present, findProjectRoot throws before the check ever runs.
 *     override fun exists(path: FsPath): Boolean =
 *         path == workingDirectory || path == workingDirectory / "gradlew"
 *     override fun isDirectory(path: FsPath): Boolean = path == workingDirectory
 *     override fun list(directory: FsPath): List<FsPath> = emptyList()
 * }
 * arch.process(LayoutCheck(), fileSystem) shouldBe emptyList()
 * ```
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public fun <R> Architecture.process(
    processor: ArchitectureProcessor<Unit, R>,
    fileSystem: KatachiFileSystem,
): R = process(processor, Unit, fileSystem)

/**
 * [process] with arguments, against [fileSystem].
 *
 * ## Example 1: run an argument-taking processor against an in-memory tree
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
 *
 * val arch = architecture { "domain".group { "UseCase" { } } }
 * val fileSystem = object : KatachiFileSystem {
 *     override val workingDirectory: FsPath = FsPath.of("/repo")
 *     override fun exists(path: FsPath): Boolean = path == workingDirectory
 *     override fun isDirectory(path: FsPath): Boolean = path == workingDirectory
 *     override fun list(directory: FsPath): List<FsPath> = emptyList()
 * }
 * arch.process(CountRoles, CountRoles.Args(prefix = ""), fileSystem) shouldBe 1
 * ```
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public fun <Args, R> Architecture.process(
    processor: ArchitectureProcessor<Args, R>,
    args: Args,
    fileSystem: KatachiFileSystem,
): R = processor.process(RealArchitectureProcessContext(this, args, fileSystem))

/**
 * [process] against [fileSystem] with the processor written inline. The file system comes first
 * so that the block stays a trailing lambda.
 *
 * ## Example 1: run an inline processor against the same kind of file system
 * ```kt
 * val arch = architecture { "domain".group { "UseCase" { } } }
 * val fileSystem = object : KatachiFileSystem {
 *     override val workingDirectory: FsPath = FsPath.of("/repo")
 *     override fun exists(path: FsPath): Boolean = path == workingDirectory
 *     override fun isDirectory(path: FsPath): Boolean = path == workingDirectory
 *     override fun list(directory: FsPath): List<FsPath> = emptyList()
 * }
 * val roleCount = arch.process(fileSystem) { context -> context.roles.size }
 * roleCount shouldBe 1
 * ```
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public fun <R> Architecture.process(
    fileSystem: KatachiFileSystem,
    block: (ArchitectureProcessContext<Unit>) -> R,
): R = block(RealArchitectureProcessContext(this, Unit, fileSystem))
