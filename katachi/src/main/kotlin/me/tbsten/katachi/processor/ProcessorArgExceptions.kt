package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException

/**
 * No processor of the run knows some `--arg` name that was passed.
 *
 * A key is accepted when any selected processor of the run takes it. A key nobody takes is
 * almost always a typo, and ignoring it would let the processor run with a default value
 * instead of the one that was meant.
 *
 * A template's parameter declared inside a branch such as `if (withImpl) { }` is known only on
 * runs whose values take that branch. The message says so when that is possible: then the key
 * is spelled right, and the fix is the value that opens its branch.
 *
 * ## Example 1: a mistyped `--arg` key
 * ```kt
 * object GenerateOne : ArchitectureProcessor<GenerateOne.Args, Unit> {
 *     override val argsSerializer: KSerializer<Args> = Args.serializer()
 *
 *     override fun process(context: ArchitectureProcessContext<Args>): Result<Unit> =
 *         runCatching { context.log("generating for ${context.args.roleName}") }
 *
 *     @Serializable
 *     data class Args(val roleName: String)
 * }
 * ```
 *
 * ```sh
 * # registered as "generate"; `roleNam` is not a field of Args
 * ./gradlew :architecture-test:katachiGenerate --arg roleNam=X
 * ```
 *
 * @property unknown the `--arg` keys that matched no processor's arguments.
 * @property known every `--arg` key the selected processors do accept.
 * @property knownDependsOnValues whether some of [known][KatachiUnknownProcessorArgException.known]
 *   was named by a processor for this run's values, so that other values could have made an
 *   [unknown][KatachiUnknownProcessorArgException.unknown] key known.
 */
@ExperimentalKatachiApi
public class KatachiUnknownProcessorArgException internal constructor(
    public val unknown: Set<String>,
    public val known: Set<String>,
    public val knownDependsOnValues: Boolean = false,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Unknown processor argument(s): ${unknown.sorted().joinToString(", ")}.")
        appendLine(
            "None of the selected processors declare a field of that name, so passing it " +
                "silently -- rather than failing -- would let a misspelled --arg key run as if " +
                "it had never been given, with the field's default value instead.",
        )
        append(
            if (known.isEmpty()) {
                "The selected processors take no arguments."
            } else {
                "Known arguments: ${known.sorted().joinToString(", ")}."
            },
        )
        if (knownDependsOnValues) {
            appendLine()
            append(
                "Some of these were named for this run's values, as a template's parameters are: " +
                    "one declared inside a branch such as `if (withImpl) { }` is known only when " +
                    "the run's values take that branch. If the key is spelled as declared, pass " +
                    "the value that opens its branch.",
            )
        }
    },
)

/**
 * A processor's `@Serializable` arguments could not be read from the `--arg` values handed to it.
 *
 * [Throwable.cause] is the underlying `SerializationException`, whose message names the field.
 *
 * ## Example 1: a required field left out of `--arg`
 * ```kt
 * @Serializable
 * data class Args(val roleName: String)
 *
 * shouldThrow<KatachiInvalidProcessorArgException> {
 *     decodeFromStringMap(Args.serializer(), emptyMap())
 * }
 * ```
 *
 * @property serialName the argument class's `@Serializable` serial name.
 */
@ExperimentalKatachiApi
public class KatachiInvalidProcessorArgException internal constructor(
    public val serialName: String,
    cause: Throwable,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Could not read the arguments of $serialName from --arg values.")
        appendLine(cause.message)
        append(
            "Processor arguments may be String, Int, Long, Short, Byte, Float, Double, " +
                "Boolean, Char, an enum, or a List/Set of one of those. Give a field a default " +
                "value if it is fine to leave out of --arg.",
        )
    },
    cause = cause,
)

/**
 * A processor's argument type holds a shape `--arg key=value` cannot express: a `Map` field, or
 * a nested `@Serializable` class.
 *
 * ## Example 1: a `Map` field is refused rather than half-read
 * ```kt
 * @Serializable
 * data class Args(val labels: Map<String, String>)
 *
 * shouldThrow<KatachiUnsupportedProcessorArgException> {
 *     decodeFromStringMap(Args.serializer(), mapOf("labels" to "a=1"))
 * }
 * ```
 *
 * @property serialName the serial name of the unsupported structure.
 * @property kind the `SerialKind` that was refused, as text.
 */
@ExperimentalKatachiApi
public class KatachiUnsupportedProcessorArgException internal constructor(
    public val serialName: String,
    public val kind: String,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Processor arguments cannot hold $serialName ($kind).")
        appendLine(
            "A --arg key=value line is one flat value per key, so it cannot express a Map or a " +
                "nested @Serializable class.",
        )
        append(
            "Flatten the field, or read it as a List<String> and build the structure inside " +
                "the processor.",
        )
    },
)

/**
 * The arguments of processors combined with `+` were decoded from something other than `--arg` values.
 *
 * Run the combined processor through `Architecture.process(...)`, or decode each processor's own
 * `Args` separately.
 *
 * ## Example 1: a combined serializer only decodes from `--arg` values
 * ```kt
 * val combined = LayoutCheck() + FileConstraintCheck()
 * shouldThrow<KatachiProcessorArgsDecoderException> {
 *     Json.decodeFromString(combined.argsSerializer, "{}")
 * }
 * ```
 *
 * @property decoder the class name of the decoder that was used.
 * @see plus
 */
@ExperimentalKatachiApi
public class KatachiProcessorArgsDecoderException internal constructor(
    public val decoder: String,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Combined processor arguments were handed to $decoder.")
        appendLine(
            "The serializer `plus` builds only works with katachi's own StringMapDecoder, so " +
                "it is not a general-purpose serializer and cannot decode from another format.",
        )
        append(
            "Run the combined processor through Architecture.process(...), or decode each " +
                "processor's own Args separately if you need another format.",
        )
    },
)

/**
 * The arguments of processors combined with `+` were asked to be encoded, which katachi does not support.
 *
 * Read each processor's own `Args` value directly instead.
 *
 * ## Example 1: a combined serializer cannot write values back
 * ```kt
 * val combined = LayoutCheck() + FileConstraintCheck()
 * shouldThrow<KatachiProcessorArgsNotEncodableException> {
 *     Json.encodeToString(combined.argsSerializer, Unit to Unit)
 * }
 * ```
 *
 * @property serialName the combined serializer's serial name.
 * @see plus
 */
@ExperimentalKatachiApi
public class KatachiProcessorArgsNotEncodableException internal constructor(
    public val serialName: String,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Processor arguments of $serialName cannot be encoded.")
        appendLine(
            "katachi only reads processor arguments from --arg key=value; there is no format " +
                "to write them back out to.",
        )
        append("Read each processor's own Args value directly if you need it.")
    },
)
