package me.tbsten.katachi.template

import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.files.internal.findProjectRoot
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.templateParameterNames
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.internal.fileSystem
import me.tbsten.katachi.template.internal.TEMPLATE_ARG
import me.tbsten.katachi.template.internal.captureNamesOf
import me.tbsten.katachi.template.internal.declaredTemplatesOf
import me.tbsten.katachi.template.internal.missingCaptureException
import me.tbsten.katachi.template.internal.requireNoConflicts
import me.tbsten.katachi.template.internal.requireNoDuplicateTemplates
import me.tbsten.katachi.template.internal.requireValidSpecifiers
import me.tbsten.katachi.template.internal.resolveTemplate
import me.tbsten.katachi.template.internal.splitTemplateArg
import me.tbsten.katachi.template.internal.templateFilesFor
import me.tbsten.katachi.template.internal.writeTemplateFiles

/**
 * Writes the files [Args.template] names into the project: one per specifier, together, as one
 * set.
 *
 * `architecture { }` already knows where every kind of file may live. This is that knowledge used
 * for something other than refusing: a `.template { }` block already sits on the file declaration
 * whose path names where it goes, so generation only has to fill that path's captures in with the
 * run's values and write the result.
 *
 * ## What it reads and what it writes
 *
 * A template's own parameters -- `stringParameter()` and its siblings -- and its captures arrive
 * as ordinary `--arg` entries and are read off [ArchitectureProcessContext.rawArgs], because a
 * `@Serializable` class cannot declare a field per template. Which of them are allowed is decided
 * before any processor runs; this object only fills in what it was given.
 *
 * **The output is the repository itself, not `build/`.** Documentation generation defaults to a
 * directory below `build/`, which keeps it out of `gitTracked()` and so out of the check. There is
 * no such net here, so every generated path is checked against its own declared pattern and
 * against leaving the project before anything is written.
 *
 * **Two or more specifiers write together, as one set.** If any one of them fails -- a value is
 * missing, a file is already there under [OnExisting.Fail], two templates resolve to the same
 * path -- nothing is written, exactly as running one alone would leave nothing half done.
 *
 * ## What it answers
 *
 * `success` once the files are written, or once [OnExisting.Skip] left them alone. Everything
 * that stops it from writing -- an unknown or ambiguous specifier, a missing or invalid capture
 * value, a file already there, a mistake in the definition -- is a `failure` carrying that
 * exception. Nothing is thrown out of `process`.
 *
 * ## Example 1: generate one template from code
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 *
 * projectArchitecture.process(
 *     GenerateCodeFromTemplate,
 *     GenerateCodeFromTemplate.Args(template = listOf("Changelog")),
 * ).getOrThrow()
 * ```
 *
 * From code, a template's own parameters cannot be given: `process` takes [Args] and nothing
 * else, and they differ per template, so they are not fields of it. This runs a template whose
 * parameters all have defaults. A template that needs `--arg name=...` is run from the command
 * line, as in Example 3.
 *
 * ## Example 2: leave the whole set alone when any of it is already there
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.OnExisting
 *
 * projectArchitecture.process(
 *     GenerateCodeFromTemplate,
 *     GenerateCodeFromTemplate.Args(template = listOf("UseCase"), onExisting = OnExisting.Skip),
 * ).getOrThrow()
 * ```
 *
 * ## Example 3: generate a pair together, from the command line
 *
 * No registration or configuration needed: the templates' own parameters and captures are `--arg`
 * names this module accepts without asking.
 * ```sh
 * # No registration needed: the Gradle plugin registers it under `template`.
 * ./gradlew :architecture-test:katachiTemplate \
 *     --arg template=data.Repository.repository,data.Repository.repositoryImpl --arg name=User
 * ```
 *
 * ## Example 4: generate below a module, named with `capture()`
 *
 * A wildcard module key names no single module, so the layout names its `*`s and the run gives
 * their values.
 * ```sh
 * # layout { ":feature:${capture("feature")}".module { "*Screen.kt".file().template { ... } } }
 * ./gradlew :architecture-test:katachiTemplate \
 *     --arg template=feature.Screen --arg feature=home --arg name=Home
 * ```
 *
 * @see Args
 * @see OnExisting
 * @see KatachiExistingTemplateFileException
 */
@ExperimentalKatachiApi
public object GenerateCodeFromTemplate : ArchitectureProcessor<GenerateCodeFromTemplate.Args, Unit> {
    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override fun process(context: ArchitectureProcessContext<Args>): Result<Unit> =
        runProcessorCatching {
            val files = templateFilesFor(
                context = context,
                specifiers = context.args.template,
                values = context.rawArgs,
            )

            // Resolved after the files are built, so that a definition problem is reported without
            // the project root ever being searched for -- the same order documentation generation
            // keeps.
            val projectRoot = File(findProjectRoot(context.fileSystem).path.value)
            context.log("Generating ${files.size} files under ${fileUri(absolutePathOf(projectRoot.path))}")
            writeTemplateFiles(
                projectRoot = projectRoot,
                files = files,
                onExisting = context.args.onExisting,
                log = { message -> context.log(message) },
            )
        }

    /**
     * The parameters `--arg template=` chose, so that the run's own check knows them.
     *
     * Exactly the names every chosen template's `template { }` declared for this run's values,
     * together with the names its `layout { }` gave its wildcards with `capture(...)` -- not
     * "anything", which is what would put a hole in the check this answers for. Reading nothing
     * off disk is what keeps deciding "is this key a typo" free of a project walk: [resolveTemplate]
     * and [templateParameterNames] both work from the declarations alone.
     *
     * When those values leave the names in doubt -- a value that decides a branch could not be
     * read, or was missing -- the run is failed here with that value's own exception, before any
     * processor runs, rather than judged by names that may belong to the wrong branch.
     */
    override fun undeclaredArgNames(context: ArchitectureProcessContext<*>): Set<String> {
        val raw = context.rawArgs[TEMPLATE_ARG] ?: return emptySet()
        // Deliberately not caught. A specifier no template answers to used to be swallowed here
        // and turned into an empty set, which made the template's own parameters unknown
        // arguments -- so a typo in `--arg template=` reported `name` as the misspelling and
        // never mentioned the real one. The run cannot succeed either way, and the first thing
        // wrong is the one worth saying.
        val specifiers = requireValidSpecifiers(splitTemplateArg(raw))
        val table = declaredTemplatesOf(context.declaredEntries)
        val chosen = specifiers.map { resolveTemplate(it, table) }
        requireNoDuplicateTemplates(specifiers, chosen)
        requireNoConflicts(chosen, context.rawArgs)

        val names = linkedSetOf<String>()
        for (template in chosen) {
            val captureNames = captureNamesOf(template.entries)
            val declared = templateParameterNames(
                template.template,
                template.role.qualifiedName,
                context.rawArgs,
                captureNames,
                isPreview = false,
            )
            names += captureNames
            if (declared.isUnreliable) {
                // A value that decides a branch could not be read or was missing, so the names
                // above may belong to a branch the real run does not take: judging by them would
                // report a parameter of the real branch as unknown and never say what is wrong.
                // The render of the same values is certain to fail with the real cause, and it
                // touches no disk, so it is thrown here, before any processor runs.
                evaluateTemplate(
                    template.template,
                    template.role.qualifiedName,
                    context.rawArgs,
                    captureNames,
                    isPreview = false,
                ) { missing, declaredAt, cause ->
                    throw missingCaptureException(template.role, template.entries, missing, declaredAt, fileName = null, cause = cause)
                }
            }
            names += declared.declared
        }
        return names
    }

    /**
     * Which templates to run, together as one set, and what to do about a file that is already
     * there.
     *
     * A template's own parameters are deliberately **not** here. They differ from template to
     * template, and a `@Serializable` class is one fixed set of fields -- so they travel as plain
     * `--arg` entries and are read off [ArchitectureProcessContext.rawArgs] instead.
     *
     * The two values can be given three ways: from code, as `--arg` on the command line, or as the
     * module's default in the Gradle plugin's `template { }` block. A `--arg` wins over the block.
     *
     * ## Example 1: from code, a set of two templates from a group
     * ```kt
     * import me.tbsten.katachi.template.GenerateCodeFromTemplate
     * import me.tbsten.katachi.template.OnExisting
     *
     * GenerateCodeFromTemplate.Args(
     *     template = listOf("data.Repository.repository", "data.Repository.repositoryImpl"),
     *     onExisting = OnExisting.Skip,
     * )
     * ```
     *
     * ## Example 2: from the command line
     * ```sh
     * ./gradlew :architecture-test:katachiTemplate \
     *     --arg template=domain.UseCase --arg onExisting=skip --arg name=GetUser
     * ```
     *
     * ## Example 3: as the module's default
     * ```kts
     * // architecture-test/build.gradle.kts
     * import me.tbsten.katachi.gradle.KatachiOnExisting
     *
     * katachi {
     *     processors {
     *         template {
     *             onExisting = KatachiOnExisting.SKIP
     *         }
     *     }
     * }
     * ```
     */
    @Serializable
    public data class Args(
        /**
         * The templates to run, each by `role.id` (or `role` alone while that role has one
         * template), a leading group written `group.role.id`. `--arg template=a,b` splits on `,`.
         *
         * No default: there is no sensible template to pick for someone who did not say.
         */
        val template: List<String>,
        /** What to do when any of the files these templates produce is already on disk. */
        val onExisting: OnExisting = OnExisting.Fail,
    )
}

/**
 * What [GenerateCodeFromTemplate] does when a file it would write is already there.
 *
 * **All three decide about the whole set, never about one file.** A run is a set of files that
 * belong together, so writing the ones that were free and leaving the rest produces a tree that
 * nothing on disk explains. `--arg onExisting=` is spelled apart from documentation generation's
 * `--arg mode=` on purpose: every selected processor's arguments share one namespace in a run, and
 * two different questions must not answer to one word.
 *
 * ## Example 1: pick the answer from the command line
 * ```kt
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.OnExisting
 *
 * // ./gradlew katachiTemplate --arg template=UseCase \
 * //   --arg onExisting=overwrite
 * GenerateCodeFromTemplate.Args(template = listOf("UseCase"), onExisting = OnExisting.Overwrite)
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
@ExperimentalKatachiApi
@Serializable
public enum class OnExisting {
    /** Write nothing and fail. The default: there is no undo below a generator. */
    @SerialName("fail")
    Fail,

    /**
     * Write nothing and succeed, which is what makes re-running a template harmless.
     *
     * It skips the run rather than the file, for the reason above: a partly regenerated set is
     * worse than one that was left alone.
     */
    @SerialName("skip")
    Skip,

    /** Replace what is there. */
    @SerialName("overwrite")
    Overwrite,
}
