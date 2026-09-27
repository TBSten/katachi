package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map

/*
 * Short runs of operations that set up the rare transitions on purpose. Single random operations
 * reach them only by luck: a result on screen is needed first, then a definition that differs in
 * the right way, then the one operation that brings it in. A regression that only shows there
 * (a result kept over a list that no longer has any template) took a few hundred sequences to hit
 * with single operations alone; with these runs mixed in, the default iterations reach it.
 */

/** How a changed definition reaches the screen: ⟳, a Gradle sync, or the "definition changed" banner and then ⟳. */
private val bringIn: Arb<List<Op>> = Arb.element(
    listOf(Op.TitleButton),
    listOf(Op.Sync),
    listOf(Op.DefinitionChanged, Op.TitleButton),
)

/** A later definition of the catalog (the first is the one already loaded). */
private val laterWorld: Arb<Op> = Arb.int(1..3).map { Op.Redefine(it) }

/** Checks a row, fills its first text field and generates: a result on screen when the row allows it. */
private val generated: Arb<List<Op>> = Arb.bind(Arb.int(0..15), Arb.int(0..15)) { row, field ->
    listOf(Op.Check(row), Op.Type(field, 1), Op.Generate, Op.Release)
}

/** A result on screen, then the definition changes under it and is brought in. */
private val resultThenRedefine: Arb<List<Op>> = Arb.bind(generated, laterWorld, bringIn) { generate, redefine, bring ->
    generate + redefine + bring
}

/** Generate pressed while a reload is still running (E-41), and the reload bringing a changed definition. */
private val generateWhileLoading: Arb<List<Op>> = Arb.bind(Arb.int(0..15), Arb.int(0..15), laterWorld) { row, field, redefine ->
    listOf(Op.NextLoad(RunOutcome.Waits), redefine, Op.TitleButton, Op.Check(row), Op.Type(field, 1), Op.Generate, Op.Release, Op.Release)
}

/** A sync that arrives while a result is on screen, with the modules changed or not. */
private val syncOverResult: Arb<List<Op>> = Arb.bind(generated, laterWorld) { generate, redefine ->
    generate + redefine + Op.Sync
}

/**
 * One step of a generated sequence: mostly a single [opArb], sometimes one of the runs above.
 * Flattened by [opsArb], so shrinking still drops single operations out of a run.
 */
internal val stepArb: Arb<List<Op>> = Arb.choose(
    17 to opArb.map { listOf(it) },
    1 to resultThenRedefine,
    1 to generateWhileLoading,
    1 to syncOverResult,
)
