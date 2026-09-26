package me.tbsten.katachi.template

import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.templateParameterNames
import me.tbsten.katachi.fs.internal.findProjectRoot
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.internal.fileSystem
import me.tbsten.katachi.template.internal.templateFiles
import me.tbsten.katachi.template.internal.templateOf
import me.tbsten.katachi.template.internal.templateRoleOf
import me.tbsten.katachi.template.internal.writeTemplateFiles

/**
 * The `--arg` name that says which role to run. Spelled once, because
 * [GenerateCodeFromTemplate.undeclaredArgNames] has to read it out of the raw arguments before
 * anything has decoded [GenerateCodeFromTemplate.Args].
 */
private const val ROLE_NAME_ARG: String = "roleName"

/**
 * Writes the files a role's `template { }` produces into the project.
 *
 * `architecture { }` already knows where every kind of file may live. This is that knowledge used
 * for something other than refusing: the template names files, the role's `layout { }` says which
 * directory each of them belongs in, and the two are matched with `Glob.matches` so a generated
 * file cannot be somewhere the next `assert()` calls unexpected.
 *
 * ## What it reads and what it writes
 *
 * The values of the template's own parameters arrive as ordinary `--arg` entries and are read off
 * [ArchitectureProcessContext.rawArgs], because a `@Serializable` class cannot declare a field per
 * role. Which of them are allowed is decided before any processor runs; this object only fills in
 * what it was given.
 *
 * **The output is the repository itself, not `build/`.** Documentation generation defaults to a
 * directory below `build/`, which keeps it out of `gitTracked()` and so out of the check. There is
 * no such net here, so the two things that could put a file somewhere unintended are closed
 * instead: a file name may hold no separator, and the directory comes only from the layout.
 *
 * ## What it answers
 *
 * `success` once the files are written, or once [OnExisting.Skip] left them alone. Everything
 * that stops it from writing -- a file already there under [OnExisting.Fail], a reserved or unsafe
 * file name, a symlink in the way, a mistake in the definition -- is a `failure` carrying that
 * exception. Nothing is thrown out of `process`.
 *
 * ## Example 1: generate the files of one role from code
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 *
 * projectArchitecture.process(
 *     GenerateCodeFromTemplate,
 *     GenerateCodeFromTemplate.Args(roleName = "Changelog"),
 * ).getOrThrow()
 * ```
 *
 * From code, the template's own parameters cannot be given: `process` takes [Args] and nothing
 * else, and the parameters differ per role, so they are not fields of it. This runs a template
 * whose parameters all have defaults. A template that needs `--arg name=...` is run from the
 * command line, as in Example 3.
 *
 * ## Example 2: leave the whole set alone when any of it is already there
 * ```kt
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.OnExisting
 *
 * projectArchitecture.process(
 *     GenerateCodeFromTemplate,
 *     GenerateCodeFromTemplate.Args(roleName = "UseCase", onExisting = OnExisting.Skip),
 * ).getOrThrow()
 * ```
 *
 * ## Example 3: run it from the command line
 *
 * No registration or configuration needed: the template's own parameters are `--arg` names the
 * processor does not declare, and this module accepts them without asking.
 * ```sh
 * # No registration needed: the Gradle plugin registers it under `template`.
 * ./gradlew :architecture-test:runKatachiProcessor --processor=template \
 *     --arg roleName=UseCase --arg name=GetUser
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
            val files = templateFiles(
                context = context,
                roleName = context.args.roleName,
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
     * The parameters `--arg roleName=` named, so that the run's own check knows them.
     *
     * Exactly the names that role's `template { }` declared for this run's values -- not
     * "anything", which is what would put a hole in the check this answers for. Reading nothing
     * off disk is what keeps deciding "is this key a typo" free of a project walk: [templateRoleOf]
     * and [me.tbsten.katachi.dsl.internal.templateParameterNames] both work from the declarations
     * alone.
     *
     * When those values leave the names in doubt -- a value that decides a branch could not be
     * read, or was missing -- the run is failed here with that value's own exception, before any
     * processor runs, rather than judged by names that may belong to the wrong branch.
     */
    override fun undeclaredArgNames(context: ArchitectureProcessContext<*>): Set<String> {
        val roleName = context.rawArgs[ROLE_NAME_ARG] ?: return emptySet()
        // Deliberately not caught. A roleName no role answers to used to be swallowed here and
        // turned into an empty set, which made the template's own parameters unknown arguments --
        // so `--arg roleName=Servce --arg name=Greeting` reported `name` as the misspelling and
        // never mentioned `Servce`. The run cannot succeed either way, and the first thing wrong
        // is the one worth saying: templateRoleOf lists the roles that do have a template.
        val role = templateRoleOf(context.roles, roleName)
        val template = templateOf(role)
        val names = templateParameterNames(template, role.qualifiedName, context.rawArgs)
        if (names.isUnreliable) {
            // A value that decides a branch could not be read or was missing, so the names above
            // may belong to a branch the real run does not take: judging by them would report a
            // parameter of the real branch as unknown and never say what is wrong. Nor can every
            // key be let through -- the check is one union over the run, so that would also wave
            // through a typo meant for another processor of it, which would then run on a default.
            // The render of the same values is certain to fail with the real cause, and it touches
            // no disk, so it is thrown here, before any processor runs -- like a roleName above.
            evaluateTemplate(template, role.qualifiedName, context.rawArgs)
        }
        return names.declared
    }

    /**
     * Which role's template to run, and what to do about a file that is already there.
     *
     * The template's own parameters are deliberately **not** here. They differ from role to role,
     * and a `@Serializable` class is one fixed set of fields -- so they travel as plain `--arg`
     * entries and are read off [ArchitectureProcessContext.rawArgs] instead.
     *
     * The two values can be given three ways: from code, as `--arg` on the command line, or as the
     * module's default in the Gradle plugin's `template { }` block. A `--arg` wins over the block.
     *
     * ## Example 1: from code, naming a role whose plain name two groups share
     * ```kt
     * import me.tbsten.katachi.template.GenerateCodeFromTemplate
     * import me.tbsten.katachi.template.OnExisting
     *
     * GenerateCodeFromTemplate.Args(roleName = "domain/UseCase", onExisting = OnExisting.Skip)
     * ```
     *
     * ## Example 2: from the command line
     * ```sh
     * ./gradlew :architecture-test:runKatachiProcessor --processor=template \
     *     --arg roleName=domain/UseCase --arg onExisting=skip --arg name=GetUser
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
     *             roleName = "domain/UseCase"
     *             onExisting = KatachiOnExisting.SKIP
     *         }
     *     }
     * }
     * ```
     */
    @Serializable
    public data class Args(
        /**
         * The role to run, by its name (`UseCase`) or its qualified name (`domain/UseCase`).
         *
         * It has no default: there is no sensible role to pick for someone who did not say.
         */
        val roleName: String,
        /** What to do when any of the files the template produces is already on disk. */
        val onExisting: OnExisting = OnExisting.Fail,
    )
}

/**
 * What [GenerateCodeFromTemplate] does when a file it would write is already there.
 *
 * **All three decide about the whole set, never about one file.** A template is a set of files that
 * belong together, so writing the three that were free and leaving the two that were not produces a
 * tree that nothing on disk explains. `--arg onExisting=` is spelled apart from documentation
 * generation's `--arg mode=` on purpose: every selected processor's arguments share one namespace
 * in a run, and two different questions must not answer to one word.
 *
 * ## Example 1: pick the answer from the command line
 * ```kt
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.OnExisting
 *
 * // ./gradlew runKatachiProcessor --processor=template --arg roleName=UseCase \
 * //   --arg onExisting=overwrite
 * GenerateCodeFromTemplate.Args(roleName = "UseCase", onExisting = OnExisting.Overwrite)
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
     * It skips the run rather than the file, for the reason above: a partly regenerated role is
     * worse than one that was left alone.
     */
    @SerialName("skip")
    Skip,

    /** Replace what is there. */
    @SerialName("overwrite")
    Overwrite,
}
