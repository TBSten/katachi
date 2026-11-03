package me.tbsten.katachi.processor

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.internal.StringMapDecoder
import me.tbsten.katachi.processor.internal.withArgs

/**
 * Two processors as one, reporting into a single list.
 *
 * Both run on **one walk of the project**, so nothing is traversed twice. Fields of the same name
 * in the two argument types are shared: the same `--arg` value reaches both halves.
 *
 * The combined arguments can only be decoded from `--arg` values, not from JSON or another
 * format. Composition is for building a run in code; to run several registered processors from
 * the command line, use the `katachiProcessors` task.
 *
 * ## What the combined answer is
 *
 * Both halves always run. The combination passes only when both do, and then its list is the
 * two lists joined. When a half fails, that failure is the answer -- the first half's when both
 * fail, with the second's attached as suppressed. To merge two checks' violations into one
 * report, pass them to `assert(check, more)` or `assertNoErrors(check, more)` instead.
 *
 * ## Example 1: run two processors as one, on one walk
 * ```kt
 * import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
 * import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
 * import me.tbsten.katachi.processor.plus
 * import me.tbsten.katachi.processor.process
 *
 * object GroupNames : ArchitectureProcessorNoArg<List<String>> {
 *     override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> =
 *         runCatching { context.groups.map { it.qualifiedName } }
 * }
 *
 * object RoleNames : ArchitectureProcessorNoArg<List<String>> {
 *     override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> =
 *         runCatching { context.roles.map { it.qualifiedName } }
 * }
 *
 * val names = projectArchitecture.process(GroupNames + RoleNames, Unit to Unit).getOrThrow()
 * ```
 *
 * @see KatachiProcessorArgsDecoderException
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

// A name declared by both is registered once: building the descriptor twice with the same name throws.
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
