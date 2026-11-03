package me.tbsten.katachi.ide.internal

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import me.tbsten.katachi.InternalKatachiApi
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
 * Writes the whole definition as JSON for the katachi IDE plugin, with the files each layout entry matched.
 *
 * The JSON holds the groups and roles, each layout entry with its matched files, the
 * constraints, how `ktFileContent { }` judged each top-level declaration, and the templates. The
 * IDE plugin runs it through the `katachiInternalDefinitionJson` task; there is no need to run it
 * by hand.
 *
 * It walks the project, as `assert()` does. When the walk cannot run (no project root, no git),
 * the run still succeeds with the declared entries only, and the JSON's `problems` says why. A
 * template that cannot be previewed is reported the same way.
 *
 * ## What it answers
 *
 * A [DefinitionSummary]. A file that cannot be written is a `failure` carrying
 * [KatachiDefinitionJsonIoException]. A definition with a mistake in it is a `failure` carrying
 * that [me.tbsten.katachi.KatachiDeclarationException], and the file from the last good run is
 * kept. Nothing is thrown out of `process`.
 *
 * ## Example 1: write the JSON from code
 * ```kt
 * import me.tbsten.katachi.ide.internal.DescribeDefinition
 * import me.tbsten.katachi.processor.process
 *
 * projectArchitecture.process(
 *     DescribeDefinition,
 *     DescribeDefinition.Args(output = "build/katachi/definition.json"),
 * ).getOrThrow()
 * ```
 *
 * ## Example 2: from the command line, where the Gradle plugin passes `output` for you
 * ```sh
 * ./gradlew :architecture-test:katachiInternalDefinitionJson
 * ```
 *
 * @see DescribeDefinition.Args
 * @see me.tbsten.katachi.template.DescribeTemplates
 */
@InternalKatachiApi
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
     * Where [DescribeDefinition] writes the JSON, and how much of the walk to put in it.
     *
     * ## Example 1: in code
     * ```kt
     * DescribeDefinition.Args(output = "build/katachi/definition.json", maxMatchedFiles = 50)
     * ```
     *
     * ## Example 2: on the command line
     * ```sh
     * ./gradlew :architecture-test:katachiInternalDefinitionJson --arg maxMatchedFiles=50
     * ```
     *
     * ## Example 3: as a default in `build.gradle.kts`
     * ```kts
     * katachi {
     *     processors {
     *         args("internalDefinitionJson") { arg("maxMatchedFiles", "50") }
     *     }
     * }
     * ```
     *
     * A `--arg` of the same name wins over the build script.
     *
     * @constructor Takes the file to write and the limits of what goes in it.
     */
    @Serializable
    public data class Args(
        /**
         * The file to write, relative to the working directory (under Gradle, the module directory) or
         * absolute. The Gradle task passes it for you.
         */
        val output: String,
        /** How many files each layout entry lists at most; `matchedCount` is exact regardless. `0` lists none. */
        val maxMatchedFiles: Int = 200,
        /**
         * Whether to explain which line allowed or rejected each top-level declaration of the files a `ktFileContent { }` covers.
         *
         * Off, `fileDeclarations` is `null` and the run reads no Kotlin source.
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
@InternalKatachiApi
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
