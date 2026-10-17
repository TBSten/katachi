package me.tbsten.katachi.ide

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.ide.internal.DefinitionJson
import me.tbsten.katachi.ide.internal.DefinitionJsonOptions
import me.tbsten.katachi.ide.internal.TemplatePreviewFailures
import me.tbsten.katachi.ide.internal.definitionJsonOf
import me.tbsten.katachi.internal.KatachiVersion
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.internal.projectWalk
import me.tbsten.katachi.template.internal.encodeToJson
import me.tbsten.katachi.template.internal.templateDescriptionOf
import me.tbsten.katachi.template.internal.writeFileAtomically

/**
 * Writes the whole definition as JSON for the katachi IDE plugin: its groups and roles, each
 * layout entry with the files it matched, the constraints, how `ktFileContent { }` decided each
 * top-level declaration of the files it covers, and the templates.
 *
 * The IDE plugin reads this file; it is not meant to be run by hand. The Gradle plugin registers
 * it under the internal key `internalDefinitionJson`, with `output` set to
 * `build/katachi/internalDefinitionJson/definition.json`, and the plugin runs
 * `katachiInternalDefinitionJson` when it needs the definition. The file replaces the older
 * `internalTemplatesJson` for the plugin: its `templates` key holds that file's contents as they
 * are.
 *
 * It walks the project, as `assert()` does, to list the files each entry matched. A walk that
 * cannot run -- no project root, no git -- does not fail the run: the entries are then the
 * declared ones, without their files, and the JSON's `problems` says why. So does a template that
 * cannot be previewed, and a constraint that cannot explain its declarations.
 *
 * ## What it answers
 *
 * A [DefinitionSummary]: how many roles, entries, constraints and templates it wrote, and where. A
 * file that cannot be written is a `failure` carrying [KatachiDefinitionJsonIoException]. Nothing
 * is thrown out of `process`.
 *
 * ## When the definition itself is wrong
 *
 * A definition whose layout cannot be flattened because of a mistake in it -- a
 * [me.tbsten.katachi.KatachiDeclarationException] -- is a `failure` carrying that exception, and
 * the file is **not** written: the one left from the last good run stays, rather than being
 * replaced by a description with no layout. Under the Gradle plugin the run also writes
 * `definition-errors.json` next to it, naming the mistake and the line it was written on. An
 * environment that cannot be walked (no git, no project root) stays a `problems` entry as before.
 *
 * ## Example 1: write the JSON from code, and from the command line
 * ```kt
 * import me.tbsten.katachi.ide.DescribeDefinition
 * import me.tbsten.katachi.processor.process
 *
 * projectArchitecture.process(
 *     DescribeDefinition,
 *     DescribeDefinition.Args(output = "build/katachi/internalDefinitionJson/definition.json"),
 * ).getOrThrow()
 * ```
 *
 * From the command line, where the Gradle plugin passes `output` for you:
 * ```sh
 * ./gradlew :architecture-test:katachiInternalDefinitionJson
 * ```
 *
 * @see DescribeDefinition.Args
 * @see me.tbsten.katachi.template.DescribeTemplates
 */
@ExperimentalKatachiApi
public object DescribeDefinition : ArchitectureProcessor<DescribeDefinition.Args, DefinitionSummary> {
    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override fun process(context: ArchitectureProcessContext<Args>): Result<DefinitionSummary> =
        runProcessorCatching {
            val args = context.args
            // The modules a placement needs are listed by the same project root the walk needs, so
            // when they cannot be, the walk's own problem usually says why; one the walk does not
            // explain is passed on as [placementsFailure].
            val placed = catching { templateDescriptionOf(context) }
            val described = placed.getOrElse { templateDescriptionOf(context, withPlacements = false) }
            val json = definitionJsonOf(
                walk = context.projectWalk,
                templates = described.json,
                placementsFailure = placed.exceptionOrNull(),
                previewFailures = TemplatePreviewFailures(described.templateFailures, described.templateSetFailures),
                options = DefinitionJsonOptions(
                    maxMatchedFiles = args.maxMatchedFiles,
                    explainDeclarations = args.explainDeclarations,
                    maxExplainedFiles = args.maxExplainedFiles,
                    katachiVersion = KatachiVersion.current(),
                    generatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                ),
            )
            writeFileAtomically(args.output, encodeToJson(DefinitionJson.serializer(), json)) { failure ->
                KatachiDefinitionJsonIoException(output = args.output, cause = failure)
            }
            context.log("Wrote ${fileUri(absolutePathOf(args.output))}")
            DefinitionSummary(
                roles = json.roles.size,
                entries = json.roles.sumOf { it.layout.size },
                constraints = json.roles.sumOf { it.constraints.size },
                templates = json.templates.templates.size,
                output = args.output,
            )
        }

    /**
     * Where to write the JSON, and how much of the walk to put in it.
     *
     * ## Example 1: the same arguments from code, from the command line and from the build script
     * ```kt
     * import me.tbsten.katachi.ide.DescribeDefinition
     *
     * DescribeDefinition.Args(output = "build/katachi/internalDefinitionJson/definition.json", maxMatchedFiles = 50)
     * ```
     * ```sh
     * ./gradlew katachiInternalDefinitionJson --arg maxMatchedFiles=50
     * ```
     * ```kts
     * // build.gradle.kts
     * katachi {
     *     processors {
     *         args("internalDefinitionJson") { arg("maxMatchedFiles", "50") }
     *     }
     * }
     * ```
     *
     * @constructor Takes the file to write and the limits of what goes in it.
     */
    @Serializable
    public data class Args(
        /**
         * The file to write, relative to the working directory -- under Gradle, the module
         * directory -- or absolute. Required: the processor does not know the key it was
         * registered under, so the registration says where its output goes.
         */
        val output: String,
        /** How many files each layout entry lists at most; `matchedCount` is exact regardless. `0` lists none. */
        val maxMatchedFiles: Int = 200,
        /**
         * Whether to explain, for each file a `ktFileContent { }` covers, which line allowed or
         * rejected each top-level declaration. Off, `fileDeclarations` is `null` and the run reads
         * no Kotlin source.
         */
        val explainDeclarations: Boolean = true,
        /** How many files get their declarations explained, in all; past it, `problems` says so. */
        val maxExplainedFiles: Int = 2000,
    )
}

/**
 * What [DescribeDefinition] wrote: how much of the definition, and where.
 *
 * ## Example 1: read back what a run wrote
 * ```kt
 * val summary = projectArchitecture.process(DescribeDefinition, DescribeDefinition.Args(output = "definition.json")).getOrThrow()
 * println("${summary.roles} roles in ${summary.output}")
 * ```
 *
 * @constructor Takes the counts and the path written.
 */
@ExperimentalKatachiApi
public class DefinitionSummary internal constructor(
    /** How many roles the JSON describes. */
    public val roles: Int,
    /** How many layout entries, over every role. */
    public val entries: Int,
    /** How many constraints, over every role. */
    public val constraints: Int,
    /** How many templates. */
    public val templates: Int,
    /** The file written, as `--arg output=` spelled it. */
    public val output: String,
) {
    /** `<n> roles, <n> entries, <n> constraints, <n> templates`: the line after `[OK]`. */
    override fun toString(): String =
        "${countOf(roles, "role")}, ${countOf(entries, "entry", "entries")}, " +
            "${countOf(constraints, "constraint")}, ${countOf(templates, "template")}"

    private fun countOf(count: Int, one: String, many: String = "${one}s"): String =
        if (count == 1) "1 $one" else "$count $many"
}
