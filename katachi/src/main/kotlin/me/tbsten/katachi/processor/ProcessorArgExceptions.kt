package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException

/**
 * No processor of the run knows some `--arg` name that was passed.
 *
 * The check is done once for the whole run, against the union of every selected processor's
 * arguments, so a name only `template` knows is accepted even while running alongside `docs`,
 * which takes none. A key that belongs to nobody is almost always a typo, and silently
 * ignoring it -- the way `kotlinx-serialization-properties` does -- would turn that typo into
 * a processor quietly running with a default value instead of the one that was meant.
 *
 * A processor may also name arguments for this run's values alone -- a template does, for its
 * parameters -- and then a name declared inside a branch such as `if (withImpl) { }` is known only
 * on the runs whose values take that branch. The message says so when that is possible, since
 * such a key is spelled right and the fix is the value that opens its branch, not the key.
 *
 * ## Example 1: catch a mistyped `--arg` key
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
 *
 * val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
 *     runProcessors(
 *         architecture = architecture { },
 *         registry = mapOf("generate" to GenerateOne::class.java),
 *         processorKeys = listOf("generate"),
 *         rawArgs = mapOf("roleNam" to "X", "roleName" to "Y"),
 *     )
 * }
 * thrown.known shouldContain "roleName"
 * ```
 *
 * @property unknown the `--arg` keys that matched no processor's arguments.
 * @property known every `--arg` key the selected processors do accept.
 * @property knownDependsOnValues whether some of [known] was named by a processor for this run's
 *   values, so that other values could have made an [unknown] key known.
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
 * A processor's `@Serializable` arguments could not be read from the `--arg` values handed to
 * it.
 *
 * Wraps [Throwable.cause], the underlying `SerializationException` `decodeFromStringMap`
 * caught, whose own message names the field.
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
 * @property kind the [kotlinx.serialization.descriptors.SerialKind] that was refused, as text.
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
 * An `argsSerializer` combined with `+` (see the `ArchitectureProcessor` combination in
 * `ProcessorComposition.kt`) was handed to a [kotlinx.serialization.encoding.Decoder] other than
 * katachi's own `StringMapDecoder`.
 *
 * ## Example 1: a combined serializer only decodes from `--arg` values
 * ```kt
 * @OptIn(ExperimentalSerializationApi::class)
 * object NotAStringMapDecoder : AbstractDecoder() {
 *     override val serializersModule: SerializersModule = EmptySerializersModule()
 *     override fun decodeElementIndex(descriptor: SerialDescriptor): Int = CompositeDecoder.DECODE_DONE
 * }
 *
 * val combined = (LayoutCheck() + FileConstraintCheck()).argsSerializer
 * shouldThrow<KatachiProcessorArgsDecoderException> {
 *     combined.deserialize(NotAStringMapDecoder)
 * }
 * ```
 *
 * @property decoder the class name of the decoder that was used.
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
 * An `argsSerializer` combined with `+` (see the `ArchitectureProcessor` combination in
 * `ProcessorComposition.kt`) was asked to encode.
 *
 * ## Example 1: a combined serializer has nowhere to write values back to
 * ```kt
 * @OptIn(ExperimentalSerializationApi::class)
 * object NoOpEncoder : AbstractEncoder() {
 *     override val serializersModule: SerializersModule = EmptySerializersModule()
 * }
 *
 * val combined = (LayoutCheck() + FileConstraintCheck()).argsSerializer
 * shouldThrow<KatachiProcessorArgsNotEncodableException> {
 *     combined.serialize(NoOpEncoder, Unit to Unit)
 * }
 * ```
 *
 * @property serialName the combined serializer's serial name.
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
