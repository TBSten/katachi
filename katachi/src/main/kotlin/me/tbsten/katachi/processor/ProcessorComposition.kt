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
 * arguments only works with katachi's own [StringMapDecoder], so it is not a serializer in the
 * general sense and would fail against JSON. Composition is for building a run in code; the
 * CLI's `--processor=A,B` takes a different route.
 *
 * **Fields of the same name in the two argument types are shared, not rejected.** The same
 * `--arg` value is handed to both halves. That is a deliberate bet that one name means one
 * thing; two processors using one name for two different things is the author's problem.
 *
 * ## Example 1: run the layout check and the constraint check as one processor
 * ```kt
 * val combined = LayoutCheck() + KonsistCheck()
 * val violations = projectArchitecture.process(combined, Unit to Unit)
 * violations.map { it.path } shouldContain "notes.md"
 * ```
 */
@ExperimentalKatachiApi
public operator fun <Args1, Args2, R> ArchitectureProcessor<Args1, List<R>>.plus(
    other: ArchitectureProcessor<Args2, List<R>>,
): ArchitectureProcessor<Pair<Args1, Args2>, List<R>> = CombinedProcessor(this, other)

internal class CombinedProcessor<Args1, Args2, R>(
    private val first: ArchitectureProcessor<Args1, List<R>>,
    private val second: ArchitectureProcessor<Args2, List<R>>,
) : ArchitectureProcessor<Pair<Args1, Args2>, List<R>> {
    override val argsSerializer: KSerializer<Pair<Args1, Args2>> =
        CombinedArgsSerializer(first.argsSerializer, second.argsSerializer)

    override fun process(context: ArchitectureProcessContext<Pair<Args1, Args2>>): List<R> {
        val (firstArgs, secondArgs) = context.args
        // Only the arguments are swapped; the walk rides along, so composing does not add a
        // second traversal.
        return first.process(context.withArgs(firstArgs)) +
            second.process(context.withArgs(secondArgs))
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
internal class CombinedArgsSerializer<A, B>(
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
