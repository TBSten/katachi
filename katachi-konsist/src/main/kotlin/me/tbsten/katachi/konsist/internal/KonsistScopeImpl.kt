package me.tbsten.katachi.konsist.internal

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.provider.KoBaseProvider
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoPathProvider
import java.io.File
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason
import me.tbsten.katachi.konsist.KatachiKonsistUnlocatableDeclarationException
import me.tbsten.katachi.konsist.KonsistScope

// A data class so that one declaration rejected twice collapses to one entry; rejections with different reasons stay separate.
internal data class RejectedElement(
    val absolutePath: String,
    val name: String?,
    val line: Int?,
    val reason: String? = null,
    val howToFix: List<String> = emptyList(),
)

// Counts expectations: a block that queries but never ends the query rejects nothing, which a report cannot tell from a rule every file satisfies.
internal class KonsistScopeImpl(scope: KoScope) : KonsistScope, KoScope by scope {
    private val collected = LinkedHashSet<RejectedElement>()

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

    override fun <T : KoBaseProvider> List<T>.must(
        reason: String,
        howToFix: List<String>,
        predicate: (T) -> Boolean,
    ) {
        asked++
        for (element in this) {
            if (!predicate(element)) collected += rejectedElementOf(element).copy(reason = reason, howToFix = howToFix)
        }
    }

    override fun <T : KoBaseProvider> List<T>.mustNot(
        reason: String,
        howToFix: List<String>,
        predicate: (T) -> Boolean,
    ) {
        asked++
        for (element in this) {
            if (predicate(element)) collected += rejectedElementOf(element).copy(reason = reason, howToFix = howToFix)
        }
    }

    override fun <T : KoBaseProvider> List<T>.mustBeEmpty(reason: String, howToFix: List<String>) {
        asked++
        for (element in this) collected += rejectedElementOf(element).copy(reason = reason, howToFix = howToFix)
    }
}

// `as?` on purpose: a path is required (a report block opens with one), a name and a line are what the element happens to carry.
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

// Konsist's `location` can throw when there is none, so the read itself is wrapped rather than the value merely checked.
internal fun lineOf(element: KoBaseProvider, path: String): Int? {
    val location = (element as? KoLocationProvider)
        ?.let { runCatching { it.location }.getOrNull() }
        ?: return null
    // `"$path:$line:$column"`. The path is stripped as a prefix rather than split on `:`,
    // because a Windows path carries one of its own at the drive letter.
    val rest = location.removePrefix("$path:")
    if (rest == location) return null
    return rest.substringBefore(':').toIntOrNull()
}

internal fun RejectedElement.toFileConstraintFailure(projectRoot: String): FileConstraintFailure =
    FileConstraintFailure(
        file = absolutePath.removePrefix("$projectRoot/"),
        declaration = name,
        line = line,
        reasons = reason?.let { listOf(FileConstraintReason(REASON_LABEL, it)) }.orEmpty(),
        howToFix = howToFix.map { FileConstraintFix(it) },
    )

private const val REASON_LABEL: String = "Reason"

