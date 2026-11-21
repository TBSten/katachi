@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konture.KontureConstraintScope
import me.tbsten.katachi.konture.internal.KontureSymbols
import me.tbsten.katachi.konture.internal.KontureSymbolsKey
import me.tbsten.katachi.konture.konture

/**
 * Konture's graph is made of the files a constraint covers and nothing else — not their
 * directories — while the names in them are resolved against every file the check walked.
 *
 * The graph is observed through a file no one can read: parsing it into the graph would fail, so
 * a run that stays clean proves it is not there. (The symbol table does read it, and Konture's
 * scan skips a file it cannot read.) Konture's own loader would have read whole source
 * directories; this backend builds the graph from the covered paths alone. The specs after the
 * first three are the other side of that choice: what the covered set and the walk do and do not
 * let Konture see.
 */
class KontureParseTargetSpec : FreeSpec({
    "layout が覆わない同じディレクトリのファイルはグラフに入れず、読めなくても制約は落ちない" {
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

    "layout が覆わない下のディレクトリのファイルはグラフに入れない" {
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
        // symbols handed to the parser, Konture resolves it to `com.example.other.Helper`, which
        // is what `notDependOnPackages` on that package sees.
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
            // The symbols katachi will build from the project's files, here this one alone, so that the cache key matches.
            KontureSymbols.of(listOf(asked)).parse(elsewhere)

            val violations = kontureRunIn(root) { classes { should().beInternal() } }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}:${it.line}" } shouldContainExactly
                listOf("src/PublicThing.kt:PublicThing:3")
        }
    }

    "2 つのワイルドカード import の下の名前" - {
        // `UserRepository` could come from either `com.example.data.*` or `com.example.domain.*`.
        // Konture picks the package that declares it, which it can only know from the symbol
        // table; the class lives in another role, so a table of the covered files alone left the
        // name unresolved and these rules green.
        val rules: List<Triple<String, String, KontureConstraintScope.() -> Unit>> = listOf(
            Triple("functions { notDependOnPackages }", "WildViewModel.load") {
                functions { should() notDependOnPackages listOf("com.example.data..") }
            },
            Triple("properties { notDependOnPackages }", "WildViewModel.repo") {
                properties { should() notDependOnPackages listOf("com.example.data..") }
            },
            Triple("classes { notReferenceClass }", "WildViewModel") {
                classes { should().notReferenceClass("com.example.data.UserRepository") }
            },
        )

        for ((spelling, declaration, rule) in rules) {
            "別の役割が宣言するクラスに解決され、$spelling が拾う" {
                wildcardProject { root ->
                    val violations = wildcardRun(root, rule = rule)

                    violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
                    violations.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}" }.distinct() shouldContainExactly
                        listOf("src/wild/WildViewModel.kt:$declaration")
                }
            }
        }

        for ((spelling, _, rule) in rules) {
            "どの役割も置かないディレクトリは walk が入らず記号表にも入らないので、$spelling は拾わない" {
                // `src/data` is declared by no role, so the walk reports it and goes no further: the
                // table holds what the layout check read, not whatever happens to be on disk.
                wildcardProject { root ->
                    val violations = wildcardRun(root, dataRole = false, rule = rule)

                    violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
                    violations.filterIsInstance<UnsatisfiedFileConstraint>().shouldBeEmpty()
                }
            }
        }

        "規則にかかるのは覆ったファイルだけで、記号表のために読んだ別の役割のファイルは判定されない" {
            wildcardProject { root ->
                val violations = wildcardRun(root) { classes { should().beInternal() } }

                violations.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}" } shouldContainExactly
                    listOf("src/wild/WildViewModel.kt:WildViewModel")
            }
        }

        "1 回の検査で記号表は 1 つで、どの konture { } もそれを読む" {
            // A constraint evaluated first leaves an empty table in the run's memo. Had either
            // `konture { }` built a table of its own, it would know `UserRepository` and be red.
            val seed = FileConstraint { subject ->
                subject.memo(KontureSymbolsKey, KontureSymbols::class) { KontureSymbols.of(emptyList()) }
                emptyList()
            }
            val rule: KontureConstraintScope.() -> Unit =
                { functions { should() notDependOnPackages listOf("com.example.data..") } }
            wildcardProject("src/wild2/OtherViewModel.kt" to OTHER_VIEW_MODEL_KT) { root ->
                val unseeded = wildcardRun(root, secondRole = true, rule = rule)
                unseeded.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}" } shouldContainExactly listOf(
                    "src/wild/WildViewModel.kt:WildViewModel.load",
                    "src/wild2/OtherViewModel.kt:OtherViewModel.load",
                )

                val seeded = wildcardRun(root, secondRole = true, firstRole = { fileConstraint("seed", check = seed) }, rule = rule)

                seeded.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
                seeded.filterIsInstance<UnsatisfiedFileConstraint>().shouldBeEmpty()
            }
        }
    }
})

/** [use] run over a fixture holding the explorer's wildcard shape: `src/wild/`, `src/data/` and `src/domain/`, plus [extra]. */
private fun <T> wildcardProject(vararg extra: Pair<String, String>, use: (File) -> T): T = fixtureProject(
    "src/wild/WildViewModel.kt" to WILD_VIEW_MODEL_KT,
    "src/data/UserRepository.kt" to USER_REPOSITORY_KT,
    "src/domain/GetUser.kt" to DOMAIN_GET_USER_KT,
    *extra,
    use = use,
)

/**
 * One run over [root] with `src/wild/` under its own `konture { }` and, unless [dataRole] is
 * false, `src/data/` and `src/domain/` held by a role with no constraint. [secondRole] adds
 * `src/wild2/` under a `konture { }` of its own; [firstRole] is written into a role that comes
 * before them all and holds the project root marker.
 */
private fun wildcardRun(
    root: File,
    dataRole: Boolean = true,
    secondRole: Boolean = false,
    firstRole: RoleScope.() -> Unit = {},
    rule: KontureConstraintScope.() -> Unit,
): List<Violation> {
    val projectArchitecture = architecture {
        files = wholeTree()
        "app".group {
            "Root".role {
                firstRole()
                layout { "gradlew".file() }
            }
            "Wild".role {
                layout {
                    "src/wild" {
                        "*.kt".file()
                        "規約".konture { rule() }
                    }
                }
            }
            if (secondRole) {
                "Wild2".role {
                    layout {
                        "src/wild2" {
                            "*.kt".file()
                            "規約".konture { rule() }
                        }
                    }
                }
            }
            if (dataRole) {
                "Data".role {
                    layout {
                        "src/data" / "*.kt".file()
                        "src/domain" / "*.kt".file()
                    }
                }
            }
        }
    }
    return projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
}

/** The explorer's `WildViewModel`: a bare `UserRepository` under two wildcard imports, its class on line 6. */
private val WILD_VIEW_MODEL_KT: String = """
    package com.example.wild

    import com.example.data.*
    import com.example.domain.*

    class WildViewModel(private val repo: UserRepository) {
        fun load(): String = repo.find("1")
    }
""".trimIndent() + "\n"

/** The same shape in a second package, for a second constraint over other files. */
private val OTHER_VIEW_MODEL_KT: String = """
    package com.example.wild2

    import com.example.data.*
    import com.example.domain.*

    class OtherViewModel(private val repo: UserRepository) {
        fun load(): String = repo.find("2")
    }
""".trimIndent() + "\n"

/** The class [WILD_VIEW_MODEL_KT] names, in the first of the two wildcard-imported packages. */
private val USER_REPOSITORY_KT: String = """
    package com.example.data

    interface UserRepository { fun find(id: String): String }
""".trimIndent() + "\n"

/** A class in the second package, so that both wildcard imports name a package the project has. */
private val DOMAIN_GET_USER_KT: String = """
    package com.example.domain

    class GetUser
""".trimIndent() + "\n"

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
