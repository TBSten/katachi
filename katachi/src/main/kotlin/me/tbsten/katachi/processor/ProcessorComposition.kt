package me.tbsten.katachi.processor

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Two checks as one, reporting into a single list.
 *
 * Both run on **one walk of the project**: the combined processor hands each half the same
 * context with only the arguments swapped, so nothing is traversed twice and the two halves
 * cannot disagree about the files they are both describing.
 *
 * `@ExperimentalKatachiApi` for a concrete reason: the serializer behind the combined
 * arguments only works with katachi's own `StringMapDecoder`, so it is not a serializer in the
 * general sense and would fail against JSON. Composition is for building a run in code; the
 * CLI's `--processor=A,B` takes a different route.
 *
 * **Fields of the same name in the two argument types are shared, not rejected.** The same
 * `--arg` value is handed to both halves. That is a deliberate bet that one name means one
 * thing; two processors using one name for two different things is the author's problem.
 *
 * ## What the combined answer is
 *
 * Both halves always run. The combination passes only when both do, and then its list is the
 * two lists joined. When a half answers with a failure, that failure is the combination's
 * answer -- the first half's when both fail, with the second's attached as a suppressed
 * exception -- and a half that throws ends the call. A failing half's list is whatever its
 * failure carries: to have two checks' violations merged into one report, pass them to
 * `validate(check, more)` or `assert(check, more)` instead.
 *
 * ## Example 1: run two processors as one, on one walk
 * ```kt
 * import me.tbsten.katachi.processor.ArchitectureProcessContext
 * import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
 * import me.tbsten.katachi.processor.plus
 * import me.tbsten.katachi.processor.process
 *
 * object GroupNames : ArchitectureProcessorNoArg<List<String>> {
 *     override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> =
 *         runCatching { context.groups.map { it.qualifiedName } }
 * }
 *
 * object RoleNames : ArchitectureProcessorNoArg<List<String>> {
 *     override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> =
 *         runCatching { context.roles.map { it.qualifiedName } }
 * }
 *
 * val names = projectArchitecture.process(GroupNames + RoleNames, Unit to Unit).getOrThrow()
 * ```
 */
@ExperimentalKatachiApi
public operator fun <Args1, Args2, R> ArchitectureProcessor<Args1, List<R>>.plus(
    other: ArchitectureProcessor<Args2, List<R>>,
): ArchitectureProcessor<Pair<Args1, Args2>, List<R>> = CombinedProcessor(this, other)

private class CombinedProcessor<Args1, Args2, R>(
    private val first: ArchitectureProcessor<Args1, List<R>>,
    private val second: ArchitectureProcessor<Args2, List<R>>,
) : ArchitectureProcessor<Pair<Args1, Args2>, List<R>> {
    override val argsSerializer: KSerializer<Pair<Args1, Args2>> =
        CombinedArgsSerializer(first.argsSerializer, second.argsSerializer)

    override fun process(context: ArchitectureProcessContext<Pair<Args1, Args2>>): Result<List<R>> {
        val (firstArgs, secondArgs) = context.args
        // Only the arguments are swapped; the walk rides along, so composing does not add a
        // second traversal.
        val firstResult = first.process(context.withArgs(firstArgs))
        val secondResult = second.process(context.withArgs(secondArgs))
        // A `KatachiArchitectureAssertionError` from both halves cannot be merged here: `check`
        // is a later layer than `processor`, so this layer cannot name it. The first failure
        // wins and the second rides along as suppressed rather than being dropped.
        return runCatching {
            val firstList = firstResult.onFailure { firstFailure ->
                // `addSuppressed` refuses the exception itself, which both halves may share.
                secondResult.exceptionOrNull()
                    ?.takeIf { it !== firstFailure }
                    ?.let(firstFailure::addSuppressed)
            }.getOrThrow()
            firstList + secondResult.getOrThrow()
        }
    }

    override fun toString(): String = "($first + $second)"
}

/**
 * The two argument types seen as one flat set of `--arg` names.
 *
 * A name declared by both is registered once, and the whole `values` map is handed to both
 * halves, so such a name reaches both. Building the descriptor twice with the same name would
 * throw, which is the only reason the deduplication has to be explicit.
 */
private class CombinedArgsSerializer<A, B>(
    private val aSerializer: KSerializer<A>,
    private val bSerializer: KSerializer<B>,
) : KSerializer<Pair<A, B>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CombinedArgs") {
        val seen = mutableSetOf<String>()
        for (source in listOf(aSerializer.descriptor, bSerializer.descriptor)) {
            for (index in 0 until source.elementsCount) {
                val name = source.getElementName(index)
                if (seen.add(name)) element(name, source.getElementDescriptor(index))
            }
        }
    }

    override fun deserialize(decoder: Decoder): Pair<A, B> {
        val source = decoder as? StringMapDecoder
            ?: throw KatachiProcessorArgsDecoderException(decoder::class.java.name)
        return aSerializer.deserialize(StringMapDecoder(source.values)) to
            bSerializer.deserialize(StringMapDecoder(source.values))
    }

    override fun serialize(encoder: Encoder, value: Pair<A, B>): Unit =
        throw KatachiProcessorArgsNotEncodableException(descriptor.serialName)
}
