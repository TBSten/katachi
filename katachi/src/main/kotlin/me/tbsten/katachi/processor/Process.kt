package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.internal.RealFileSystem

/**
 * Runs [processor] against this definition and the real project, and returns its answer.
 *
 * The [Result] is the processor's own, handed back untouched. A processor answers everything,
 * its failures included, through that [Result] -- see [ArchitectureProcessor] -- so nothing is
 * caught here: one that throws out of `process` anyway throws out of this call too.
 *
 * Reading the project is deferred to [ArchitectureProcessContext.filesOf], so a processor that
 * only looks at declarations does no IO -- the search for the project root included.
 *
 * ## Example 1: run a processor that takes no arguments, and fail the test on a failure
 * ```kt
 * import me.tbsten.katachi.check.LayoutCheck
 * import me.tbsten.katachi.processor.process
 *
 * val violations = projectArchitecture.process(LayoutCheck()).getOrThrow()
 * ```
 *
 * @throws me.tbsten.katachi.fs.KatachiProjectRootNotFoundException when the processor asks for
 *   files outside its own `runCatching` and no directory above the working directory carries a
 *   Gradle, Maven or git marker. katachi's own processors answer that as a `failure` instead.
 */
@ExperimentalKatachiApi
public fun <R> Architecture.process(processor: ArchitectureProcessor<Unit, R>): Result<R> =
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
 *     override fun process(context: ArchitectureProcessContext<Args>): Result<Int> =
 *         runCatching { context.roles.count { it.name.startsWith(context.args.prefix) } }
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
 * count.getOrThrow() shouldBe 3
 * ```
 */
@ExperimentalKatachiApi
public fun <Args, R> Architecture.process(
    processor: ArchitectureProcessor<Args, R>,
    args: Args,
): Result<R> = process(processor, args, RealFileSystem())

/**
 * [process] with the processor written inline, for something used once.
 *
 * The block is not an [ArchitectureProcessor], so it answers with a plain [R]: there is no run to
 * pass or fail, only a value to compute. Throwing out of the block ends the call as usual.
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
 * Internal, so this door is katachi's own: [KatachiFileSystem] is `@InternalKatachiApi`, so a
 * processor written outside `:katachi` tests against a real checkout instead.
 *
 * ## Example 1: run a processor against a file system built for one spec
 * ```kt
 * val arch = architecture {
 *     "domain".group { "UseCase" { layout { "domain" / "*UseCase.kt".file() } } }
 * }
 * val fileSystem = object : KatachiFileSystem {
 *     override val workingDirectory: FsPath = FsPath.of("/repo")
 *     // "gradlew" is one of the project root markers LayoutCheck's walk looks for; without one
 *     // present, the check answers with a failure before it looks at any file.
 *     override fun exists(path: FsPath): Boolean =
 *         path == workingDirectory || path == workingDirectory / "gradlew"
 *     override fun isDirectory(path: FsPath): Boolean = path == workingDirectory
 *     override fun list(directory: FsPath): List<FsPath> = emptyList()
 * }
 * arch.process(LayoutCheck(), fileSystem).getOrThrow() shouldBe emptyList()
 * ```
 */
internal fun <R> Architecture.process(
    processor: ArchitectureProcessor<Unit, R>,
    fileSystem: KatachiFileSystem,
): Result<R> = process(processor, Unit, fileSystem)

/**
 * [process] with arguments, against [fileSystem].
 *
 * ## Example 1: run an argument-taking processor against an in-memory tree
 * ```kt
 * object CountRoles : ArchitectureProcessor<CountRoles.Args, Int> {
 *     override val argsSerializer: KSerializer<Args> = Args.serializer()
 *
 *     override fun process(context: ArchitectureProcessContext<Args>): Result<Int> =
 *         runCatching { context.roles.count { it.name.startsWith(context.args.prefix) } }
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
 * arch.process(CountRoles, CountRoles.Args(prefix = ""), fileSystem).getOrThrow() shouldBe 1
 * ```
 */
internal fun <Args, R> Architecture.process(
    processor: ArchitectureProcessor<Args, R>,
    args: Args,
    fileSystem: KatachiFileSystem,
): Result<R> = processor.process(RealArchitectureProcessContext(this, args, fileSystem))

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
internal fun <R> Architecture.process(
    fileSystem: KatachiFileSystem,
    block: (ArchitectureProcessContext<Unit>) -> R,
): R = block(RealArchitectureProcessContext(this, Unit, fileSystem))
