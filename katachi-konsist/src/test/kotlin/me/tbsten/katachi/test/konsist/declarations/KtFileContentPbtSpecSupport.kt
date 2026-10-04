package me.tbsten.katachi.test.konsist.declarations

import kotlin.math.ceil
import kotlin.random.Random

/*
 * How the property-based specs of `ktFileContent { }` run: a seeded `kotlin.random.Random` loop, as
 * `:katachi`'s properties are written (`io.kotest.property` is not a dependency here), and a small
 * greedy shrinker so that a failure reads as the smallest case that still fails.
 *
 * The number of trials is the same idea as the IDE plugin's properties: few by default, so that
 * `./gradlew check` stays short, and `-Pkatachi.pbt.scale=N` runs N times as many
 * (`-Pkatachi.pbt.seed=N` tries another seed). katachi-konsist's build passes both on to the test
 * JVM as system properties. `KATACHI_PBT_SEED` and `KATACHI_PBT_ITERATIONS` (the environment
 * variables `:katachi`'s properties read) still work, the system properties winning.
 */

/** The seed every property of a run derives its own from. */
internal val PBT_SEED: Long =
    System.getProperty("katachi.pbt.seed")?.toLongOrNull()
        ?: System.getenv("KATACHI_PBT_SEED")?.toLongOrNull()
        ?: 20261004L

/** How many times the default number of trials to run. */
private val PBT_SCALE: Double = System.getProperty("katachi.pbt.scale")?.toDoubleOrNull() ?: 1.0

/** The trials of a property whose default is [default]: scaled, or as many as `KATACHI_PBT_ITERATIONS` says. */
internal fun pbtTrials(default: Int): Int {
    System.getenv("KATACHI_PBT_ITERATIONS")?.toIntOrNull()?.let { return it.coerceAtLeast(1) }
    return ceil(default * PBT_SCALE).toInt().coerceAtLeast(1)
}

/** How many candidates the shrinker tries in all before it reports what it has. */
private const val SHRINK_BUDGET: Int = 400

/**
 * Runs [check] over [trials] cases [generate] makes from a `Random` seeded with [seed].
 *
 * When a case fails, the candidates [shrinks] offers are tried in turn and the first that still
 * fails replaces it, until none does; the error then shows that smallest case, written by
 * [describe], with the seed that plays the run again.
 */
internal fun <T> checkTrials(
    property: String,
    trials: Int,
    seed: Long,
    generate: (Random) -> T,
    shrinks: (T) -> List<T> = { emptyList() },
    describe: (T) -> String = { it.toString() },
    check: (T) -> Unit,
) {
    val random = Random(seed)
    repeat(trials) { trial ->
        val case = generate(random)
        val firstFailure = failureOf { check(case) } ?: return@repeat
        var smallest = case
        var smallestFailure = firstFailure
        var budget = SHRINK_BUDGET
        var shrunk = true
        while (shrunk && budget > 0) {
            shrunk = false
            for (candidate in shrinks(smallest)) {
                if (budget-- <= 0) break
                val failure = failureOf { check(candidate) } ?: continue
                smallest = candidate
                smallestFailure = failure
                shrunk = true
                break
            }
        }
        throw AssertionError(
            buildString {
                appendLine("$property: trial $trial of seed $seed failed (-Pkatachi.pbt.seed=$seed plays it again).")
                appendLine("The smallest failing case:")
                appendLine(describe(smallest))
                append(smallestFailure.message ?: smallestFailure.toString())
            },
            firstFailure,
        )
    }
}

/** What [block] threw, or `null` when it returned: an assertion that failed, or the code under test crashing. */
private fun failureOf(block: () -> Unit): Throwable? =
    try {
        block()
        null
    } catch (failure: AssertionError) {
        failure
    } catch (failure: Exception) {
        failure
    }

/** One of [items], or `null` when there are none. */
internal fun <T> Random.pickOrNull(items: List<T>): T? = if (items.isEmpty()) null else items[nextInt(items.size)]

/** One of [items], which must not be empty. */
internal fun <T> Random.pick(items: List<T>): T = items[nextInt(items.size)]

/** `true` with a chance of [percent] in a hundred. */
internal fun Random.chance(percent: Int): Boolean = nextInt(100) < percent

/** [list] with [item] put at a random place. */
internal fun <T> Random.inserting(list: List<T>, item: T): List<T> = list.toMutableList().apply { add(nextInt(size + 1), item) }
