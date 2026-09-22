@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.ConstraintCheck
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.fs.RealFileSystem
import me.tbsten.katachi.konsist.KonsistScope
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.scan.Violation

/**
 * What every end-to-end spec of this module shares: a throwaway project, one role, one
 * `konsist { }`, and one run of the check over it.
 *
 * Everything is end-to-end on purpose rather than for want of a smaller seam.
 * [me.tbsten.katachi.dsl.ConstraintSubject] has an `internal constructor`, so this module
 * genuinely cannot build one — and that is the point being kept: if the konsist integration
 * ever needs something `:katachi` does not publish, it stops compiling rather than quietly
 * opting in. Adding an `@InternalKatachiApi` factory to make these specs smaller would throw
 * that proof away.
 */
@OptIn(ExperimentalKatachiApi::class)
internal fun konsistRun(
    vararg sources: Pair<String, String>,
    declarations: List<String> = listOf("*.kt"),
    block: KonsistScope.() -> Unit,
): List<Violation> = fixtureProject(*sources) { root ->
    val projectArchitecture = architecture {
        files = wholeTree()
        "domain".group {
            "UseCase" {
                layout {
                    // The fixture's own project root marker: a real file in a tree the check
                    // walks whole, so the layout has to account for it.
                    "gradlew".file()
                    "src" {
                        for (declaration in declarations) declaration.file()
                        "規約".konsist { block() }
                    }
                }
            }
        }
    }
    projectArchitecture.validate(RealFileSystem(root), ConstraintCheck())
}

/**
 * The same run with `ConstraintCheck()` left out of the arguments — the mistake the unevaluated
 * guard exists for.
 */
@OptIn(ExperimentalKatachiApi::class)
internal fun konsistRunWithoutConstraintCheck(
    vararg sources: Pair<String, String>,
    declarations: List<String> = listOf("*.kt"),
    block: KonsistScope.() -> Unit,
): List<Violation> = fixtureProject(*sources) { root ->
    val projectArchitecture = architecture {
        files = wholeTree()
        "domain".group {
            "UseCase" {
                layout {
                    "gradlew".file()
                    "src" {
                        for (declaration in declarations) declaration.file()
                        "規約".konsist { block() }
                    }
                }
            }
        }
    }
    projectArchitecture.validate(RealFileSystem(root))
}

/** A public class named `PublicThing`, declared on line 3. */
internal val PUBLIC_THING_KT: String = """
    package com.example

    class PublicThing {
        fun run() = Unit
    }
""".trimIndent() + "\n"

/** An internal class named `InternalThing`, declared on line 3. */
internal val INTERNAL_THING_KT: String = """
    package com.example

    internal class InternalThing {
        fun run() = Unit
    }
""".trimIndent() + "\n"
