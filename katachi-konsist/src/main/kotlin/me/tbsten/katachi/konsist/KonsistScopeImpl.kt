package me.tbsten.katachi.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.provider.KoBaseProvider
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoPathProvider
import java.io.File
import me.tbsten.katachi.dsl.ConstraintFailure

/**
 * One element a block rejected: enough to open a report block on, and nothing more.
 *
 * A `data class` so that the same declaration rejected twice — by two `must` calls in one
 * block, or by a query that lists it twice — collapses to one entry in the set that holds
 * these. Two different declarations of one file stay two.
 */
internal data class RejectedElement(
    /** Absolute, `/` separated, as Konsist reported it. Made project relative on the way out. */
    val absolutePath: String,
    /** What Konsist calls it, or `null` for an element carrying no name. */
    val name: String?,
    /** 1-based, or `null` when Konsist could not place it — a whole file, for instance. */
    val line: Int?,
)

/**
 * The receiver a `konsist { }` block actually runs against: Konsist's scope, plus a collector.
 *
 * Everything Konsist offers arrives by delegation, so a query reads exactly as it would in a
 * plain Konsist test. What is different is the end of the query: [must], [mustNot] and
 * [mustBeEmpty] record rather than throw, so one block can reject four declarations and the
 * report can open four blocks at four paths.
 *
 * It also counts how many times it was asked anything. A block that queries Konsist and never
 * ends the query rejects nothing, which is indistinguishable in a report from a rule every
 * file satisfies — so [expectations] is what lets `KonsistConstraint` refuse that answer
 * instead of returning it.
 */
internal class KonsistScopeImpl(scope: KoScope) : KonsistScope, KoScope by scope {
    /** Insertion ordered, so rejections come back in the order the block produced them. */
    private val collected = LinkedHashSet<RejectedElement>()

    /** How many times this block said what it wants. Zero is refused, not answered. */
    private var asked: Int = 0

    val rejected: List<RejectedElement> get() = collected.toList()

    val expectations: Int get() = asked

    override fun <T : KoBaseProvider> List<T>.must(predicate: (T) -> Boolean) {
        asked++
        for (element in this) if (!predicate(element)) collected += rejectedElementOf(element)
    }

    override fun <T : KoBaseProvider> List<T>.mustNot(predicate: (T) -> Boolean) {
        asked++
        for (element in this) if (predicate(element)) collected += rejectedElementOf(element)
    }

    override fun <T : KoBaseProvider> List<T>.mustBeEmpty() {
        asked++
        for (element in this) collected += rejectedElementOf(element)
    }
}

/**
 * What katachi can say about one rejected element.
 *
 * The three narrowings are `as?` rather than `as` on purpose, and they are not the same kind
 * of optional. A path is required — a report block opens with one, so an element without one
 * is refused rather than reported — while a name and a line are what the element happens to
 * carry: a file declaration has a name and no location at all, and the report simply says
 * less about it.
 */
private fun rejectedElementOf(element: KoBaseProvider): RejectedElement {
    val located = element as? KoPathProvider
        ?: throw KatachiKonsistUnlocatableDeclarationException(
            declaration = element::class.simpleName ?: element::class.java.name,
        )
    val path = located.path
    return RejectedElement(
        absolutePath = File(path).invariantSeparatorsPath,
        name = (element as? KoNameProvider)?.name,
        line = lineOf(element, path),
    )
}

/**
 * The 1-based line of [element], or `null` when it does not have one.
 *
 * Konsist computes `location` from PSI offsets and documents it as able to throw when there
 * is none, so the read itself is wrapped rather than the value merely checked. A line katachi
 * failed to read is a report that says a little less; a run that died reading one would be a
 * report that says nothing at all.
 */
private fun lineOf(element: KoBaseProvider, path: String): Int? {
    val location = (element as? KoLocationProvider)
        ?.let { runCatching { it.location }.getOrNull() }
        ?: return null
    // `"$path:$line:$column"`. The path is stripped as a prefix rather than split on `:`,
    // because a Windows path carries one of its own at the drive letter.
    val rest = location.removePrefix("$path:")
    if (rest == location) return null
    return rest.substringBefore(':').toIntOrNull()
}

/** One rejection, with its path written the way every other path in a report is written. */
internal fun RejectedElement.toConstraintFailure(projectRoot: String): ConstraintFailure =
    ConstraintFailure(
        file = absolutePath.removePrefix("$projectRoot/"),
        declaration = name,
        line = line,
    )
