package me.tbsten.katachi.template

import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.displayExistingPath
import me.tbsten.katachi.internal.fileUri

/**
 * A file the template would have written is already there.
 *
 * The default, because these files land in the user's own source tree: overwriting one silently is
 * how work gets lost, and there is no undo below a generator. Nothing was written -- not even the
 * files that were free -- so the repository is exactly as it was before the run.
 *
 * The katachi IDE plugin reads the first line of the message and the indented `file:///` lines
 * under it to mark the files that are in the way, so keep their shape.
 *
 * ## Example 1: re-run a template over a file it already produced
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiExistingTemplateFileException
 *
 * val thrown = shouldThrow<KatachiExistingTemplateFileException> {
 *     projectArchitecture.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(roleName = "UseCase"),
 *     ).getOrThrow()
 * }
 * thrown.existing.size shouldBe 1
 * ```
 *
 * @see OnExisting
 */
public class KatachiExistingTemplateFileException internal constructor(
    /** The paths that are already taken, relative to the project root, sorted. */
    public val existing: List<String>,
    /** How many files the template produces in total. */
    public val total: Int,
    /** The project root the paths are relative to, absolute. */
    public val projectRoot: String,
) : KatachiCheckException(
    message = buildString {
        val root = absolutePathOf(projectRoot)
        appendLine("${existing.size} of $total generated files already exist under ${fileUri(root)}:")
        for (path in existing) appendLine("  ${displayExistingPath(root, path)}")
        appendLine(
            "Nothing was written, the files that were free included: a half-applied template " +
                "leaves a tree nothing on disk explains, and these files are the repository's " +
                "own source rather than something below build/.",
        )
        append(
            "Pass `--arg onExisting=skip` to leave the whole set alone, or " +
                "`--arg onExisting=overwrite` to replace them.",
        )
    },
)

/**
 * katachi could not write one of the generated files.
 *
 * The one failure of generation that is about neither the definition nor katachi: the directory is
 * read only, the disk is full, the checkout is on a mounted volume that went away. It names the
 * project root as well as the path, because the usual surprise is *where* a layout resolved to.
 *
 * ## Example 1: say which file a failing run was writing
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiTemplateIoException
 *
 * val failure = shouldThrow<KatachiTemplateIoException> {
 *     projectArchitecture.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(roleName = "UseCase"),
 *     ).getOrThrow()
 * }
 * println("could not write ${failure.path} under ${failure.projectRoot}")
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiTemplateIoException internal constructor(
    /** The file being written, relative to [projectRoot]. */
    public val path: String,
    /** The project root the path is relative to, absolute. */
    public val projectRoot: String,
    cause: Throwable,
) : KatachiCheckException(
    message = buildString {
        val root = absolutePathOf(projectRoot)
        appendLine("""Cannot write the generated file "${displayExistingPath(root, path)}" under ${fileUri(root)}.""")
        appendLine("The filesystem refused it: ${cause::class.simpleName}: ${cause.message}")
        append(
            "Generated files go where the role's layout { } says, relative to the project root " +
                "katachi found. Check that this process may write there, and that the layout " +
                "resolves where you expect.",
        )
    },
    cause = cause,
)

/**
 * A name katachi needs while it works is already taken by a file in the source tree.
 *
 * Generated files are written to `.katachi-new` and the files they replace wait under
 * `.katachi-old`, so that a run that fails partway can put everything back. Both names belong to
 * katachi for the length of a run. A file already sitting at one of them would be written over and
 * then removed, which is the one thing generation promises never to do -- so the run stops before
 * writing anything at all.
 *
 * ## Example 1: stop rather than write over a file wearing a reserved suffix
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiReservedTemplatePathException
 *
 * val thrown = shouldThrow<KatachiReservedTemplatePathException> {
 *     projectArchitecture.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(roleName = "UseCase"),
 *     ).getOrThrow()
 * }
 * thrown.reserved.single() shouldBe "src/main/kotlin/GreetUseCase.kt.katachi-new"
 * ```
 *
 * @see OnExisting
 */
public class KatachiReservedTemplatePathException internal constructor(
    /** The taken paths, relative to the project root, sorted. */
    public val reserved: List<String>,
    /** The project root the paths are relative to, absolute. */
    public val projectRoot: String,
) : KatachiCheckException(
    message = buildString {
        val root = absolutePathOf(projectRoot)
        appendLine("${reserved.size} path(s) katachi writes through are already taken under ${fileUri(root)}:")
        for (path in reserved) appendLine("  ${displayExistingPath(root, path)}")
        appendLine(
            "The suffixes .katachi-new and .katachi-old are katachi's while a template runs: the " +
                "first holds a file before it is in place, the second holds the file it replaces " +
                "until the whole set has landed. Reusing one would write over these files and " +
                "then remove them.",
        )
        append(
            "Nothing was written. Move or delete these files, or rename them to something that " +
                "does not end in .katachi-new or .katachi-old.",
        )
    },
)

/**
 * A generated file resolved to somewhere outside the project, through a link in the source tree.
 *
 * A `layout { }` declares paths relative to the project root, and a path that climbs out of it is
 * refused where it is written. That check reads the text of the path, which is the whole of it
 * until a directory along the way turns out to be a symbolic link: `src/main/kotlin` can be a link
 * to anywhere, and following it lands a generated file in a tree the definition never described --
 * silently, since the log reports the path as declared.
 *
 * So the real location is resolved once more just before writing. The paths this names are the
 * ones the definition asked for; [resolved] is where the file system says they actually go.
 *
 * ## Example 1: refuse to follow a link out of the project
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiTemplateEscapesProjectException
 *
 * val thrown = shouldThrow<KatachiTemplateEscapesProjectException> {
 *     projectArchitecture.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(roleName = "UseCase"),
 *     ).getOrThrow()
 * }
 * thrown.path shouldBe "src/main/kotlin/GreetUseCase.kt"
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiTemplateEscapesProjectException internal constructor(
    /** The file's path as the layout declares it, relative to the project root. */
    public val path: String,
    /** The directory it actually resolves to, absolute and with every link followed. */
    public val resolved: String,
    /** The project root the path is relative to, absolute. */
    public val projectRoot: String,
) : KatachiCheckException(
    message = buildString {
        val root = absolutePathOf(projectRoot)
        appendLine("""The generated file "${displayExistingPath(root, path)}" does not land inside the project.""")
        appendLine("  project root: ${fileUri(root)}")
        appendLine("  resolves to:  ${fileUri(absolutePathOf(resolved))}")
        appendLine(
            "A directory on the way is a symbolic link out of the tree. Generation writes source " +
                "the user owns, and a file that lands where the definition does not describe is " +
                "one nothing will check afterwards.",
        )
        append(
            "Nothing was written. Point the link inside the project, or declare the layout so it " +
                "resolves where the file should go.",
        )
    },
)

/**
 * Something that is not a regular file is sitting where a generated file goes.
 *
 * Usually a directory: a role whose layout says `useCase/GetUser.kt` in a tree where `GetUser.kt`
 * is a package directory. Replacing it is not a thing generation is willing to do even under
 * [OnExisting.Overwrite], which answers for files whose contents can be put back, not for a
 * directory whose contents are not this run's to move.
 *
 * ## Example 1: refuse a target that is a directory
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiTemplateTargetNotAFileException
 *
 * val thrown = shouldThrow<KatachiTemplateTargetNotAFileException> {
 *     projectArchitecture.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(roleName = "UseCase"),
 *     ).getOrThrow()
 * }
 * thrown.blocked.single() shouldBe "useCase/GetUserUseCase.kt"
 * ```
 *
 * @see OnExisting
 */
public class KatachiTemplateTargetNotAFileException internal constructor(
    /** The paths taken by something other than a regular file, relative to the project root. */
    public val blocked: List<String>,
    /** The project root the paths are relative to, absolute. */
    public val projectRoot: String,
) : KatachiCheckException(
    message = buildString {
        val root = absolutePathOf(projectRoot)
        appendLine("${blocked.size} generated file(s) are blocked by a directory under ${fileUri(root)}:")
        for (path in blocked) appendLine("  ${displayExistingPath(root, path)}")
        appendLine(
            "onExisting decides what happens to a file that is already there, and the answer for " +
                "overwrite is that the old contents are put back if the run fails. A directory " +
                "holds files this run did not write and cannot account for, so it is never " +
                "replaced, whatever onExisting says.",
        )
        append(
            "Nothing was written. Move what is there, or declare a layout that puts the generated " +
                "file somewhere else.",
        )
    },
)
