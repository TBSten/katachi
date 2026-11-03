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
import me.tbsten.katachi.dsl.internal.TEMPLATE_ARG
import me.tbsten.katachi.template.internal.captureNamesOf
import me.tbsten.katachi.template.internal.declaredTemplatesOf
import me.tbsten.katachi.template.internal.missingCaptureException
import me.tbsten.katachi.template.internal.requireNoConflicts
import me.tbsten.katachi.template.internal.requireValidSpecifiers
import me.tbsten.katachi.template.internal.TemplateTarget
import me.tbsten.katachi.template.internal.resolveTemplateTarget
import me.tbsten.katachi.template.internal.splitTemplateArg
import me.tbsten.katachi.template.internal.templateRunFor
import me.tbsten.katachi.template.internal.writeTemplateFiles

/**
 * Writes the files [Args.template] names into the project, all of them together as one set.
 *
 * A `.template { }` block sits on a file declaration in `layout { }`, so the path is already
 * known; generation fills the path's captures with the run's values and writes the result.
 * Registered by default under the key `template`.
 *
 * ## What it reads and what it writes
 *
 * A template's own parameters (`stringParameter()` and its siblings) and its captures are passed
 * as ordinary `--arg` entries.
 *
 * **The output is the repository itself, not `build/`.** Every generated path is checked against
 * its declared pattern, and against leaving the project, before anything is written.
 *
 * **Two or more specifiers write together, as one set.** If any one of them fails -- a value is
 * missing, a file is already there under [OnExisting.Fail], two templates resolve to the same
 * path -- nothing is written.
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
 * From code, a template's own parameters cannot be given, so this runs only a template whose
 * parameters all have defaults. A template that needs `--arg name=...` is run from the command
 * line, as in Example 2.
 *
 * ## Example 2: generate a pair together, from the command line
 * ```sh
 * ./gradlew :architecture-test:katachiTemplate \
 *     --arg template=data.Repository.repository,data.Repository.repositoryImpl --arg name=User
 * ```
 *
 * ## Example 3: generate below a directory, named with `capture()`
 *
 * A wildcard directory names no single directory, so the layout names its `*`s and the run gives
 * their values.
 * ```sh
 * # layout { "feature" / capture("feature") / "${capture("name")}Screen.kt".file().template { ... } }
 * ./gradlew :architecture-test:katachiTemplate \
 *     --arg template=feature.Screen --arg feature=home --arg name=Home
 * ```
 *
 * ## Example 4: generate a template set
 *
 * A template set gathers several templates under one name, and gives each member the values its
 * own block passes with `arg(...)`. Only the set's own parameters are `--arg` names.
 * ```sh
 * # "NewFeature".templateSet { val name by stringParameter(); template("feature.Screen") { arg("name", name) } }
 * ./gradlew :architecture-test:katachiTemplate \
 *     --arg template=feature.NewFeature --arg name=Home
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
            val run = templateRunFor(
                context = context,
                specifiers = context.args.template,
                values = context.rawArgs,
            )
            val files = run.files

            // Resolved after the files are built, so that a definition problem is reported without
            // the project root ever being searched for -- the same order documentation generation
            // keeps.
            val projectRoot = File(findProjectRoot(context.fileSystem).path.value)
            val where = fileUri(absolutePathOf(projectRoot.path))
            val set = run.templateSet
            if (set != null) {
                context.log("Generating ${files.size} files of template set ${set.specifier} under $where")
            } else {
                context.log("Generating ${files.size} files under $where")
            }
            writeTemplateFiles(
                projectRoot = projectRoot,
                files = files,
                onExisting = context.args.onExisting,
                log = { message -> context.log(message) },
            )
        }

    /** The parameters and capture names of the templates `--arg template=` chose. */
    override fun undeclaredArgNames(context: ArchitectureProcessContext<*>): Set<String> {
        val raw = context.rawArgs[TEMPLATE_ARG] ?: return emptySet()
        // Deliberately not caught. A specifier no template answers to used to be swallowed here
        // and turned into an empty set, which made the template's own parameters unknown
        // arguments -- so a typo in `--arg template=` reported `name` as the misspelling and
        // never mentioned the real one. The run cannot succeed either way, and the first thing
        // wrong is the one worth saying.
        val specifiers = requireValidSpecifiers(splitTemplateArg(raw))
        val table = declaredTemplatesOf(context.declaredEntries)
        val chosen = when (val target = resolveTemplateTarget(specifiers, table, context.architecture.allTemplateSets)) {
            // The set's parameters were found when the definition was evaluated, so no member is
            // run to learn them. A member's capture or parameter given straight to --arg is not a
            // name of the set, and is refused as an unknown argument.
            is TemplateTarget.Set -> return target.declaration.parameters.mapTo(linkedSetOf()) { it.name }
            is TemplateTarget.Templates -> target.chosen
        }
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
     * Which templates to run, together as one set, and what to do about a file that is already there.
     *
     * A template's own parameters are not here: they differ from template to template, so they are
     * passed as plain `--arg` entries.
     *
     * ## Example 1: in code, a set of two templates from a group
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
     * ## Example 2: on the command line
     * ```sh
     * ./gradlew :architecture-test:katachiTemplate \
     *     --arg template=domain.UseCase --arg onExisting=skip --arg name=GetUser
     * ```
     *
     * ## Example 3: as the module's default in `build.gradle.kts`
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
     *
     * A `--arg` of the same name wins over the `template { }` block.
     *
     * @constructor Takes the templates to run and, optionally, what to do about an existing file.
     */
    @Serializable
    public data class Args(
        /**
         * The templates to run, each by `role.id`, or `role` alone while that role has one template.
         *
         * A leading group is written `group.role.id`. `--arg template=a,b` splits on `,`.
         *
         * Instead of templates, this can be one template set (`group.NewFeature`, or its name alone
         * while one set has it): its members are generated together, and nothing else may be listed
         * with it.
         */
        val template: List<String>,
        /** What to do when any of the files these templates produce is already on disk. */
        val onExisting: OnExisting = OnExisting.Fail,
    )
}

/**
 * What [GenerateCodeFromTemplate] does when a file it would write is already there.
 *
 * **Every choice applies to the whole set, never to one file**, so a run never leaves a set
 * partly regenerated.
 *
 * ## Example 1: pick the answer
 * ```kt
 * GenerateCodeFromTemplate.Args(template = listOf("UseCase"), onExisting = OnExisting.Overwrite)
 * ```
 *
 * ```sh
 * ./gradlew :architecture-test:katachiTemplate --arg template=UseCase --arg onExisting=overwrite
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

    /** Write nothing and succeed, which makes re-running a template harmless. */
    @SerialName("skip")
    Skip,

    /** Replace what is there. */
    @SerialName("overwrite")
    Overwrite,
}
