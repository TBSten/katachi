@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.KonsistScope
import me.tbsten.katachi.konsist.internal.ParsedFileCache
import me.tbsten.katachi.konsist.konsist

/**
 * Konsist is handed the files a constraint covers and nothing else — not their directories.
 *
 * Observed through a file no one can read: parsing it fails, so a run that stays clean proves
 * it was never parsed. Before, the smallest set of directories was parsed recursively and then
 * narrowed, so such a sibling broke every constraint that shared its directory.
 */
class KonsistParseTargetSpec : FreeSpec({
    beforeEach {
        // Every run here has to reach the parser, not a parse an earlier spec left behind.
        ParsedFileCache.enabled = false
    }
    afterSpec {
        ParsedFileCache.enabled = true
    }

    "layout が覆わない同じディレクトリのファイルは解析しない" {
        fixtureProject(
            "src/InternalThing.kt" to INTERNAL_THING_KT,
            "src/Unreadable.kt" to PUBLIC_THING_KT,
        ) { root ->
            val violations = withUnreadable(File(root, "src/Unreadable.kt")) {
                konsistRunIn(root, declarations = listOf("InternalThing.kt")) {
                    classes().must { it.hasInternalModifier }
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
                konsistRunIn(root, declarations = listOf("InternalThing.kt")) {
                    classes().must { it.hasInternalModifier }
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
            val violations = konsistRunIn(root, declarations = listOf("InternalThing.kt", "PublicThing.kt")) {
                classes().must { it.hasInternalModifier }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.path } shouldContainExactly
                listOf("src/PublicThing.kt")
        }
    }
})

/**
 * [use] run while [file] cannot be read.
 *
 * A JVM running as root reads it anyway; the spec would then pass without proving anything, so
 * that is failed loudly instead.
 */
private fun <T> withUnreadable(file: File, use: () -> T): T {
    file.setReadable(false, false)
    try {
        if (file.canRead()) error("Cannot make ${file.path} unreadable; this spec proves nothing as root.")
        return use()
    } finally {
        file.setReadable(true, false)
    }
}

/** One run of `FileConstraintCheck()` over an existing fixture [root]. */
private fun konsistRunIn(
    root: File,
    declarations: List<String>,
    block: KonsistScope.() -> Unit,
): List<Violation> {
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
    return projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
}
