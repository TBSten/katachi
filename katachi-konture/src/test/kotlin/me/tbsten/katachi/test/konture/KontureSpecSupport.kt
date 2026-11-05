@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konture

import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konture.KontureConstraintScope
import me.tbsten.katachi.konture.konture

/**
 * What every end-to-end spec of this module shares: a throwaway project, one role, one
 * `konture { }`, and one run of the check over it.
 *
 * Everything is end-to-end on purpose rather than for want of a smaller seam.
 * [me.tbsten.katachi.dsl.FileConstraintSubject] has an `internal constructor`, so this module
 * genuinely cannot build one — and that is the point being kept: if the konture integration
 * ever needs something `:katachi` does not publish, it stops compiling rather than quietly
 * opting in. Adding an `@InternalKatachiApi` factory to make these specs smaller would throw
 * that proof away.
 */
internal fun kontureRun(
    vararg sources: Pair<String, String>,
    declarations: List<String> = listOf("*.kt"),
    block: KontureConstraintScope.() -> Unit,
): List<Violation> = fixtureProject(*sources) { root ->
    val projectArchitecture = architecture {
        files = wholeTree()
        "domain".group {
            "UseCase".role {
                layout {
                    // The fixture's own project root marker: a real file in a tree the check
                    // walks whole, so the layout has to account for it.
                    "gradlew".file()
                    "src" {
                        for (declaration in declarations) declaration.file()
                        "規約".konture { block() }
                    }
                }
            }
        }
    }
    projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
}

/**
 * The same run with `FileConstraintCheck()` left out of the arguments — the mistake the unevaluated
 * guard exists for.
 */
internal fun kontureRunWithoutFileConstraintCheck(
    vararg sources: Pair<String, String>,
    declarations: List<String> = listOf("*.kt"),
    block: KontureConstraintScope.() -> Unit,
): List<Violation> = fixtureProject(*sources) { root ->
    val projectArchitecture = architecture {
        files = wholeTree()
        "domain".group {
            "UseCase".role {
                layout {
                    "gradlew".file()
                    "src" {
                        for (declaration in declarations) declaration.file()
                        "規約".konture { block() }
                    }
                }
            }
        }
    }
    projectArchitecture.validate(RealFileSystem(root))
}

/** One run of `FileConstraintCheck()` over an existing fixture [root], whose `src/` holds the files [declarations] name. */
internal fun kontureRunIn(
    root: File,
    declarations: List<String> = listOf("*.kt"),
    block: KontureConstraintScope.() -> Unit,
): List<Violation> {
    val projectArchitecture = architecture {
        files = wholeTree()
        "domain".group {
            "UseCase".role {
                layout {
                    "gradlew".file()
                    "src" {
                        for (declaration in declarations) declaration.file()
                        "規約".konture { block() }
                    }
                }
            }
        }
    }
    return projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
}

/**
 * [use] run while [file] cannot be read.
 *
 * A JVM running as root reads it anyway; the spec would then pass without proving anything, so
 * that is failed loudly instead.
 */
internal fun <T> withUnreadable(file: File, use: () -> T): T {
    file.setReadable(false, false)
    try {
        if (file.canRead()) error("Cannot make ${file.path} unreadable; this spec proves nothing as root.")
        return use()
    } finally {
        file.setReadable(true, false)
    }
}

/** A public class named `PublicThing` on line 3, with a public `run` on line 4 and a `var count` on line 5. */
internal val PUBLIC_THING_KT: String = """
    package com.example

    class PublicThing {
        fun run() = Unit
        var count: Int = 0
    }
""".trimIndent() + "\n"

/** An internal class named `InternalThing` on line 3, with an internal `run` on line 4 and a `val id` on line 5. */
internal val INTERNAL_THING_KT: String = """
    package com.example

    internal class InternalThing {
        internal fun run() = Unit
        val id: Int = 0
    }
""".trimIndent() + "\n"

/** A class in another package, referred to by [USES_HELPER_KT]. */
internal val HELPER_KT: String = """
    package com.example.other

    class Helper
""".trimIndent() + "\n"

/** A class on line 5 that imports [HELPER_KT]'s class and names it in a signature. */
internal val USES_HELPER_KT: String = """
    package com.example

    import com.example.other.Helper

    class UsesHelper {
        fun helper(): Helper = Helper()
    }
""".trimIndent() + "\n"

/** A view model that imports Compose: what `notDependOnPackages "androidx.compose.."` rejects. Class on line 5. */
internal val COMPOSABLE_VIEW_MODEL_KT: String = """
    package com.example

    import androidx.compose.runtime.Composable

    class HomeViewModel {
        @Composable
        fun Draw() = Unit
    }
""".trimIndent() + "\n"
