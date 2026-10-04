@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.io.File
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.konture.internal.KontureSymbols

/**
 * Konture is handed the files a constraint covers and nothing else — not their directories.
 *
 * Observed through a file no one can read: parsing it fails, so a run that stays clean proves
 * it was never parsed. Konture's own loader would have read whole source directories; this
 * backend builds the graph from the covered paths alone. The last two specs are the other side
 * of that choice: what the covered set does and does not let Konture see.
 */
class KontureParseTargetSpec : FreeSpec({
    "layout が覆わない同じディレクトリのファイルは解析しない" {
        fixtureProject(
            "src/InternalThing.kt" to INTERNAL_THING_KT,
            "src/Unreadable.kt" to PUBLIC_THING_KT,
        ) { root ->
            val violations = withUnreadable(File(root, "src/Unreadable.kt")) {
                kontureRunIn(root, declarations = listOf("InternalThing.kt")) {
                    classes { should().beInternal() }
                }
            }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            violations.filterIsInstance<UnsatisfiedFileConstraint>().shouldBeEmpty()
        }
    }

    "layout が覆わない下のディレクトリのファイルは解析しない" {
        fixtureProject(
            "src/InternalThing.kt" to INTERNAL_THING_KT,
            "src/nested/Unreadable.kt" to PUBLIC_THING_KT,
        ) { root ->
            val violations = withUnreadable(File(root, "src/nested/Unreadable.kt")) {
                kontureRunIn(root, declarations = listOf("InternalThing.kt")) {
                    classes { should().beInternal() }
                }
            }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            violations.filterIsInstance<UnsatisfiedFileConstraint>().shouldBeEmpty()
        }
    }

    "覆ったファイルはすべて判定される" {
        fixtureProject(
            "src/InternalThing.kt" to INTERNAL_THING_KT,
            "src/PublicThing.kt" to PUBLIC_THING_KT,
            "src/nested/Other.kt" to PUBLIC_THING_KT,
        ) { root ->
            val violations = kontureRunIn(root, declarations = listOf("InternalThing.kt", "PublicThing.kt")) {
                classes { should().beInternal() }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.path } shouldContainExactly
                listOf("src/PublicThing.kt")
        }
    }

    "覆った .kt 同士で型が解決される" {
        // `UsesHelper.helper()` returns `Helper`, declared in another covered file. With the
        // symbols of the covered set handed to the parser, Konture resolves it to
        // `com.example.other.Helper`, which is what `notDependOnPackages` on that package sees.
        fixtureProject(
            "src/UsesHelper.kt" to USES_HELPER_KT,
            "src/other/Helper.kt" to HELPER_KT,
        ) { root ->
            val violations = kontureRunIn(root, declarations = listOf("UsesHelper.kt", "other/Helper.kt")) {
                classes { should() notDependOnPackages "com.example.other.." }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}" } shouldContainExactly
                listOf("src/UsesHelper.kt:UsesHelper")
        }
    }

    "覆っていないクラスを経由する継承の連鎖は辿れない" {
        // `Leaf : Middle : Base`, with `Middle` outside the constraint. Konture resolves supertypes
        // against the graph, which here is the covered set, so `Leaf` is never seen to reach `Base`
        // and `that() areAssignableTo` selects `Base` alone. Covering `Middle` restores the chain.
        // This is the limitation `KontureConstraintScope` documents; the spec keeps the two honest.
        fixtureProject(
            "src/Base.kt" to BASE_KT,
            "src/Middle.kt" to MIDDLE_KT,
            "src/Leaf.kt" to LEAF_KT,
        ) { root ->
            val withoutMiddle = kontureRunIn(root, declarations = listOf("Base.kt", "Leaf.kt")) {
                classes { that() areAssignableTo "com.example.Base"; should().beInternal() }
            }
            withoutMiddle.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            withoutMiddle.filterIsInstance<UnsatisfiedFileConstraint>().map { it.declaration } shouldContainExactly
                listOf("Base")

            val withMiddle = kontureRunIn(root, declarations = listOf("Base.kt", "Middle.kt", "Leaf.kt")) {
                classes { that() areAssignableTo "com.example.Base"; should().beInternal() }
            }
            withMiddle.filterIsInstance<UnsatisfiedFileConstraint>().map { it.declaration } shouldContainExactlyInAnyOrder
                listOf("Base", "Middle", "Leaf")
        }
    }

    "同じファイルを別の綴りで先に Konture に読ませてあっても、覆ったファイルとして報告される" {
        // Konture's parse cache is keyed by canonical path and keeps the spelling of the first
        // parse, so a reader's own Konture test, or an earlier run from another working directory,
        // can leave katachi a declaration spelled `src/../src/X.kt` for the file it asks for as
        // `src/X.kt`. The graph has to accept it and the report has to name the covered spelling.
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
            val asked = File(root, "src/PublicThing.kt")
            val elsewhere = File(root, "src/../src/PublicThing.kt")
            // The symbols katachi will build for the covered set, so that the cache key matches.
            KontureSymbols.of(listOf(asked)).parse(elsewhere)

            val violations = kontureRunIn(root) { classes { should().beInternal() } }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}:${it.line}" } shouldContainExactly
                listOf("src/PublicThing.kt:PublicThing:3")
        }
    }
})

/** The root of a three-step hierarchy: `Base` on line 3. */
private val BASE_KT: String = """
    package com.example

    interface Base
""".trimIndent() + "\n"

/** The middle of it: `Middle : Base` on line 3. */
private val MIDDLE_KT: String = """
    package com.example

    interface Middle : Base
""".trimIndent() + "\n"

/** The leaf: `Leaf : Middle` on line 3, which reaches `Base` only through `Middle`. */
private val LEAF_KT: String = """
    package com.example

    class Leaf : Middle
""".trimIndent() + "\n"
