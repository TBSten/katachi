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
 * @property unknown the `--arg` keys that matched no processor's arguments.
 * @property known every `--arg` key the selected processors do accept.
 * @property notAllowed by processor key, the unknown names that processor would have accepted had
 *   the module written `acceptsUndeclaredArgs = true` for it. A hint and nothing else: a name in
 *   here was still refused, which is what keeps the flag the user's own decision.
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
 */
@ExperimentalKatachiApi
public class KatachiUnknownProcessorArgException internal constructor(
    public val unknown: Set<String>,
    public val known: Set<String>,
    public val notAllowed: Map<String, Set<String>> = emptyMap(),
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
        for ((key, names) in notAllowed.toSortedMap()) {
            appendLine()
            append(
                "\"$key\" would accept ${names.sorted().joinToString(", ")} if this module " +
                    "asked for it: ${undeclaredArgsHint(key)}.",
            )
        }
    },
)

/**
 * The processor keys the Gradle plugin gives a block of their own.
 *
 * katachi's own two processors are registered into every module by the plugin, which also gives
 * each one a typed block. Both spellings reach the same processor, and writing both is refused, so
 * a hint naming the wrong one walks the reader into that refusal instead of out of this one.
 *
 * Named here rather than asked of the plugin because this runs inside the generated entry point,
 * where no Gradle type is on the classpath. The plugin's own list is pinned to these same two
 * names by a test of its own, so registering a third processor by default fails there, naming this
 * list as the other half to change.
 */
private val TYPED_BLOCK_KEYS: Set<String> = setOf("docs", "template")

/** How to spell "let this processor take arguments it does not declare", for [key]. */
private fun undeclaredArgsHint(key: String): String = if (key in TYPED_BLOCK_KEYS) {
    "katachi { processors { $key { acceptsUndeclaredArgs = true } } }"
} else {
    """katachi { processors { args("$key") { acceptsUndeclaredArgs = true } } }"""
}

/**
 * A processor's `@Serializable` arguments could not be read from the `--arg` values handed to
 * it.
 *
 * Wraps [Throwable.cause], the underlying `SerializationException` `decodeFromStringMap`
 * caught, whose own message names the field.
 *
 * @property serialName the argument class's `@Serializable` serial name.
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
 * @property serialName the serial name of the unsupported structure.
 * @property kind the [kotlinx.serialization.descriptors.SerialKind] that was refused, as text.
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
 * [me.tbsten.katachi.processor.StringMapDecoder].
 *
 * @property decoder the class name of the decoder that was used.
 *
 * ## Example 1: a combined serializer only decodes from `--arg` values
 * ```kt
 * @OptIn(ExperimentalSerializationApi::class)
 * object NotAStringMapDecoder : AbstractDecoder() {
 *     override val serializersModule: SerializersModule = EmptySerializersModule()
 *     override fun decodeElementIndex(descriptor: SerialDescriptor): Int = CompositeDecoder.DECODE_DONE
 * }
 *
 * val combined = (LayoutCheck() + KonsistCheck()).argsSerializer
 * shouldThrow<KatachiProcessorArgsDecoderException> {
 *     combined.deserialize(NotAStringMapDecoder)
 * }
 * ```
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
 * @property serialName the combined serializer's serial name.
 *
 * ## Example 1: a combined serializer has nowhere to write values back to
 * ```kt
 * @OptIn(ExperimentalSerializationApi::class)
 * object NoOpEncoder : AbstractEncoder() {
 *     override val serializersModule: SerializersModule = EmptySerializersModule()
 * }
 *
 * val combined = (LayoutCheck() + KonsistCheck()).argsSerializer
 * shouldThrow<KatachiProcessorArgsNotEncodableException> {
 *     combined.serialize(NoOpEncoder, Unit to Unit)
 * }
 * ```
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
