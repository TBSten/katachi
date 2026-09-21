package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.RealFileSystem

/**
 * Something that consumes an architecture definition and produces [R].
 *
 * katachi had two fixed outputs — the check, and the documentation generation that comes
 * later — and no way for anyone else to add a third. This is that way in: the check is one
 * processor among others, not a privileged one.
 *
 * It is an interface rather than an abstract class so that a one-off can be written as a
 * lambda (see the `process` overload that takes one), and it is deliberately not `suspend`:
 * making it suspend would force every plain JUnit or kotest test calling into it to wrap the
 * call in `runBlocking`, which would mean a dependency on `kotlinx-coroutines-core` — and
 * katachi ships with no runtime dependencies at all. A processor that needs concurrency can
 * start it inside [process] and block there.
 *
 * [R] may be [Unit]: a processor is allowed to have effects. How it has them is its own
 * business — [ProjectModel] offers nothing to write through, so a processor that produces
 * files takes the write operation as a constructor parameter.
 *
 * ## Example 1: a processor with settings, written as a class
 * ```kt
 * class GenerateDocs(
 *     private val writeFile: (path: String, content: String) -> Unit,
 * ) : ArchitectureProcessor<Unit> {
 *     override fun process(model: ProjectModel) {
 *         for (role in model.roles) writeFile("${role.qualifiedName}.md", role.name)
 *     }
 * }
 *
 * projectArchitecture.process(GenerateDocs(writeFile = { path, text -> File(path).writeText(text) }))
 * ```
 */
@ExperimentalKatachiApi
public interface ArchitectureProcessor<out R> {
    /**
     * Reads [model] and produces this processor's result.
     *
     * ## Example 1: return something derived from the declarations
     * ```kt
     * class RoleCount : ArchitectureProcessor<Int> {
     *     override fun process(model: ProjectModel): Int = model.roles.size
     * }
     * ```
     */
    public fun process(model: ProjectModel): R
}

/**
 * An [ArchitectureProcessor] that produces nothing, which is to say one that exists for its
 * effects.
 *
 * `ArchitectureProcessor<Unit>` already says this, but it says it in a shape a reader has to
 * decode: a generic parameter that happens to be [Unit]. The alias states the intent in the
 * name instead, and it is the common case — writing files, printing a report, pushing to
 * something — so it is worth a name of its own.
 *
 * Where the effect goes is still the processor's own business. [ProjectModel] offers nothing
 * to write through, so a processor takes the write operation as a constructor parameter. That
 * is also what makes it testable, and what makes "write it" and "check it is up to date" the
 * same processor with a different lambda.
 *
 * ## Example 1: write one file per role
 * ```kt
 * class GenerateDocs(
 *     private val writeFile: (path: String, content: String) -> Unit,
 * ) : ArchitectureProcessorUnit {
 *     override fun process(model: ProjectModel) {
 *         for (role in model.roles) writeFile("${role.qualifiedName}.md", role.name)
 *     }
 * }
 *
 * projectArchitecture.process(GenerateDocs { path, text -> File(path).writeText(text) })
 * ```
 *
 * ## Example 2: the same processor, verifying instead of writing
 * ```kt
 * val stale = mutableListOf<String>()
 * projectArchitecture.process(
 *     GenerateDocs { path, text -> if (File(path).readText() != text) stale += path },
 * )
 * stale.shouldBeEmpty()
 * ```
 */
@ExperimentalKatachiApi
public typealias ArchitectureProcessorUnit = ArchitectureProcessor<Unit>

/**
 * Runs [processor] against this definition and the real project, and returns its result.
 *
 * Reading the project is deferred to `ProjectModel.filesOf`, so a processor that only looks
 * at declarations does no IO — the search for the project root included.
 *
 * ## Example 1: run a processor that has settings
 * ```kt
 * val docs = GenerateDocs(writeFile = { path, text -> File(path).writeText(text) })
 * projectArchitecture.process(docs)
 * ```
 *
 * @throws me.tbsten.katachi.fs.KatachiProjectRootNotFoundException when the processor asks
 *   for files and no directory above the working directory carries a Gradle, Maven or git
 *   marker.
 */
@ExperimentalKatachiApi
public fun <R> Architecture.process(processor: ArchitectureProcessor<R>): R =
    process(processor, RealFileSystem())

/**
 * [process] with the processor written inline, for something used once.
 *
 * ## Example 1: pull out the files of the roles you care about
 * ```kt
 * val gradleFiles = projectArchitecture.process { model ->
 *     model.roles.filter { it.name.startsWith("Gradle") }.flatMap { model.filesOf(it) }
 * }
 * ```
 */
@ExperimentalKatachiApi
public fun <R> Architecture.process(block: (ProjectModel) -> R): R =
    process(RealFileSystem(), block)

/**
 * [process] against [fileSystem], which is how katachi's own specs run a processor against a
 * tree that only exists in memory. Same deferral: [fileSystem] is untouched unless the
 * processor asks for files.
 *
 * `@InternalKatachiApi`, so this door is katachi's own: [KatachiFileSystem] is internal too, so
 * a processor written outside `:katachi` has no in-memory tree to hand in and tests its
 * processor end to end against a real checkout instead. Whether that seam becomes part of the
 * processor API is open, and step 4 will run into it again when a check arrives as a module of
 * its own.
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public fun <R> Architecture.process(
    processor: ArchitectureProcessor<R>,
    fileSystem: KatachiFileSystem,
): R = processor.process(ProjectModel(this, fileSystem))

/**
 * [process] against [fileSystem] with the processor written inline. The file system comes
 * first so that the block stays a trailing lambda.
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public fun <R> Architecture.process(
    fileSystem: KatachiFileSystem,
    block: (ProjectModel) -> R,
): R = block(ProjectModel(this, fileSystem))
