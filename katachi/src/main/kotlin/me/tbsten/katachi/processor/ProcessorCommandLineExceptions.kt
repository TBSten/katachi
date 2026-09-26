package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException

/**
 * `--entry-point` was passed more than once.
 *
 * ## Example 1: catch a command line that names two entry points
 * ```kt
 * shouldThrow<KatachiDuplicateEntryPointOptionException> {
 *     main(arrayOf("--entry-point=a.B", "--entry-point=c.D", "--processor=layout"))
 * }
 * ```
 *
 * @property argument the second `--entry-point` token, as it was written.
 */
@ExperimentalKatachiApi
public class KatachiDuplicateEntryPointOptionException internal constructor(
    public val argument: String,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("--entry-point was given more than once, the second time as \"$argument\".")
        appendLine(
            "A run reads one generated entry point, so a second one would silently decide " +
                "which architecture the whole run answers for -- by position on a command " +
                "line nobody typed by hand.",
        )
        append("Pass it exactly once, as --entry-point=<fully qualified class name>.")
    },
)

/**
 * The command line named no entry point to load.
 *
 * ## Example 1: catch a run that was started without the generated entry point
 * ```kt
 * shouldThrow<KatachiMissingEntryPointOptionException> { main(arrayOf("--processor=layout")) }
 * ```
 */
@ExperimentalKatachiApi
public class KatachiMissingEntryPointOptionException internal constructor() :
    KatachiDeclarationException(
        message = buildString {
            appendLine("--entry-point=<fully qualified class name> is required and was not given.")
            appendLine(
                "It is what tells this run which module's architecture { } to answer for, and " +
                    "the Gradle plugin always passes it -- so a run without it was started by " +
                    "hand, or by a task that is not one of the plugin's katachi<Key> tasks.",
            )
            append("Start the run with the processor's own task, e.g. ./gradlew katachiDocs.")
        },
    )

/**
 * The command line named no processor to run.
 *
 * ## Example 1: catch a run that selected nothing
 * ```kt
 * shouldThrow<KatachiMissingProcessorSelectionException> {
 *     main(arrayOf("--entry-point=com.example.GeneratedKatachiEntryPoint"))
 * }
 * ```
 */
@ExperimentalKatachiApi
public class KatachiMissingProcessorSelectionException internal constructor() :
    KatachiDeclarationException(
        message = buildString {
            appendLine("At least one --processor=<key> is required and none was given.")
            appendLine(
                "Falling back to a default -- every registered processor, or the first of " +
                    "them -- would make what runs depend on the order of a build script " +
                    "rather than on what was asked for.",
            )
            append(
                "Start the run with the processor's own task, which passes its key, e.g. " +
                    "./gradlew katachiDocs.",
            )
        },
    )

/**
 * A token on the command line is none of the three katachi reads.
 *
 * ## Example 1: catch a misspelled option
 * ```kt
 * shouldThrow<KatachiUnknownProcessorOptionException> {
 *     main(arrayOf("--entry-point=com.example.GeneratedKatachiEntryPoint", "--processors=docs"))
 * }
 * ```
 *
 * @property argument the token, as it was written.
 */
@ExperimentalKatachiApi
public class KatachiUnknownProcessorOptionException internal constructor(
    public val argument: String,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Unrecognized argument: \"$argument\".")
        appendLine(
            "Ignoring it would let a misspelled option run as though it had never been " +
                "written, which is the same accident a mistyped --arg key is.",
        )
        append(
            "Expected one of --entry-point=<fully qualified class name>, " +
                "--processor=<key>[,<key>...], --arg=<key>=<value> or " +
                "--arg-for=<processor key>:<key>=<value>.",
        )
    },
)

/**
 * An `--arg` token is not a `key=value` pair.
 *
 * Covers both halves of the same mistake: no `=` at all, and an empty key before it.
 *
 * ## Example 1: catch an --arg with nothing to split on
 * ```kt
 * shouldThrow<KatachiInvalidProcessorArgOptionException> {
 *     main(
 *         arrayOf(
 *             "--entry-point=com.example.GeneratedKatachiEntryPoint",
 *             "--processor=docs",
 *             "--arg=roleName",
 *         ),
 *     )
 * }
 * ```
 *
 * @property argument the `--arg` token, as it was written.
 */
@ExperimentalKatachiApi
public class KatachiInvalidProcessorArgOptionException internal constructor(
    public val argument: String,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("\"$argument\" is not of the form --arg=<key>=<value>.")
        appendLine(
            "The first = splits the pair, so a value may hold further = signs " +
                "(--arg=invokeImpl=TODO() is one token and one argument), but a name before " +
                "the first one is what says which field of which processor is being set.",
        )
        append("Write --arg=<key>=<value>, for example --arg=roleName=GetUser.")
    },
)

/**
 * The same `--arg` key was passed twice.
 *
 * ## Example 1: catch a key passed twice
 * ```kt
 * shouldThrow<KatachiDuplicateProcessorArgException> {
 *     main(
 *         arrayOf(
 *             "--entry-point=com.example.GeneratedKatachiEntryPoint",
 *             "--processor=template",
 *             "--arg=roleName=GetUser",
 *             "--arg=roleName=PutUser",
 *         ),
 *     )
 * }
 * ```
 *
 * @property key the argument name that was given twice.
 * @property argument the second `--arg` token, as it was written.
 */
@ExperimentalKatachiApi
public class KatachiDuplicateProcessorArgException internal constructor(
    public val key: String,
    public val argument: String,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("--arg key \"$key\" was given more than once, the second time as \"$argument\".")
        appendLine(
            "Letting the last one win would turn a typo into a silent change of value: the " +
                "run would go green having used something nobody meant to pass.",
        )
        append("Pass each --arg key once.")
    },
)

/**
 * An `--arg-for` token is not `<processor key>:<name>=<value>`, or names a processor the run did
 * not select.
 *
 * `--arg-for` gives one argument to one processor of a run that selected several; the Gradle
 * plugin's `katachiProcessors` task sends each processor's build-script arguments this way, so
 * that `docs { outputDir = ... }` reaches `docs` only.
 *
 * ## Example 1: catch an --arg-for addressed to a processor that is not running
 * ```kt
 * val thrown = shouldThrow<KatachiInvalidProcessorArgForOptionException> {
 *     main(
 *         arrayOf(
 *             "--entry-point=com.example.GeneratedKatachiEntryPoint",
 *             "--processor=layout",
 *             "--arg-for=docs:outputDir=docs/architecture",
 *         ),
 *     )
 * }
 * thrown.processorKey shouldBe "docs"
 * ```
 *
 * @property argument the `--arg-for` token, as it was written.
 * @property processorKey the processor key the token names, or `null` when the token is not of
 *   the form `<processor key>:<name>=<value>` at all.
 * @property selectedKeys the `--processor` keys of the run.
 */
@ExperimentalKatachiApi
public class KatachiInvalidProcessorArgForOptionException internal constructor(
    public val argument: String,
    public val processorKey: String?,
    public val selectedKeys: List<String>,
) : KatachiDeclarationException(
    message = buildString {
        if (processorKey == null) {
            appendLine("\"$argument\" is not of the form --arg-for=<processor key>:<name>=<value>.")
            appendLine(
                "The first : ends the processor key and the first = after it ends the name, so " +
                    "a value may hold further : and = signs, but neither the key nor the name " +
                    "may be empty.",
            )
            append("Write --arg-for=<processor key>:<name>=<value>, for example --arg-for=docs:outputDir=docs.")
        } else {
            appendLine(
                "\"$argument\" gives an argument to the processor \"$processorKey\", which this run " +
                    "did not select (selected: ${selectedKeys.joinToString(", ")}).",
            )
            appendLine(
                "Dropping it would let an argument meant for one processor vanish without a " +
                    "trace, as a misspelled key would.",
            )
            append("Add --processor=$processorKey, or remove the --arg-for.")
        }
    },
)
