@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konture

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konture.KontureConstraintScope
import me.tbsten.katachi.konture.konture
import me.tbsten.katachi.test.baseline.assertWithBaselineForTest

/**
 * What a baseline recorded over `konture { }` holds back once the project changes: a violation
 * is recorded under its declaration's name, so that name has to tell apart what a change can
 * swap — a fixed violation in one class and a new one of the same name in another.
 */
class KontureBaselineSpec : FreeSpec({
    "宣言名で baseline の記録が分かれる" - {
        "User.id を記録したあと User.id を直して Order.id を var にすると失敗する" {
            val failure = recordThenBreak(
                before = MODELS_WITH_VAR_USER_ID,
                after = MODELS_WITH_VAR_ORDER_ID,
                recorded = "User.id",
            ) {
                properties { should().beVal() }
            }

            failure.rejectedDeclarations() shouldContainExactly listOf("Order.id")
        }

        "入れ子の HomeScreen.State を記録したあと DetailScreen.State を internal にすると失敗する" {
            val failure = recordThenBreak(
                before = SCREENS_WITH_INTERNAL_HOME_STATE,
                after = SCREENS_WITH_INTERNAL_DETAIL_STATE,
                recorded = "HomeScreen.State",
            ) {
                classes { should().bePublic() }
            }

            failure.rejectedDeclarations() shouldContainExactly listOf("DetailScreen.State")
        }

        "GetUser.invoke を記録したあと GetOrder.invoke を internal にすると失敗する" {
            val failure = recordThenBreak(
                before = USE_CASES_WITH_INTERNAL_GET_USER,
                after = USE_CASES_WITH_INTERNAL_GET_ORDER,
                recorded = "GetUser.invoke",
            ) {
                functions { should().bePublic() }
            }

            failure.rejectedDeclarations() shouldContainExactly listOf("GetOrder.invoke")
        }
    }

    "名前の無いブロックは種類の名前で baseline の記録が分かれる" - {
        "名前の無い konture { } の User.id を記録したあと、名前の無い fileConstraint { } が User.id を断ると失敗する" {
            val failure = recordThenBreakUnder(
                before = USER_WITH_VAR_ID,
                after = USER_WITH_DEPRECATED_ID,
                recorded = "User.id",
            ) {
                konture { properties { should().beVal() } }
                fileConstraint { subject -> deprecatedUserIds(subject) }
            }

            failure.violations.filterIsInstance<UnsatisfiedFileConstraint>()
                .map { it.constraintName to it.declaration } shouldContainExactly listOf(null to "User.id")
        }
    }
})

/**
 * Records a baseline over [before], rewrites the file to [after] and checks again, answering
 * with the failure the second run has to end in.
 *
 * The baseline starts empty, as a project that has just written `baseline()` has it, so that the
 * record run finds nothing missing.
 */
private fun recordThenBreak(
    before: String,
    after: String,
    recorded: String,
    block: KontureConstraintScope.() -> Unit,
): KatachiArchitectureAssertionError = recordThenBreakUnder(before, after, recorded) { "規約".konture { block() } }

/** [recordThenBreak] with the constraints of `src/` written out, for a spec about how they are named. */
private fun recordThenBreakUnder(
    before: String,
    after: String,
    recorded: String,
    constraints: LayoutDirectoryScope.() -> Unit,
): KatachiArchitectureAssertionError = fixtureProject(
    "src/Models.kt" to before,
    "katachi-baseline.json" to EMPTY_BASELINE,
) { root ->
    val projectArchitecture = baselineArchitecture(constraints)
    projectArchitecture.assertWithBaselineForTest(RealFileSystem(root), FileConstraintCheck(), update = true)
    val recordedBaseline = File(root, "katachi-baseline.json").readText()

    File(root, "src/Models.kt").writeText(after)
    val failure = shouldThrow<KatachiArchitectureAssertionError> {
        projectArchitecture.assertWithBaselineForTest(RealFileSystem(root), FileConstraintCheck())
    }
    // Checked after the second run, so that a name shared by both declarations shows up as the
    // run that should have failed and did not.
    recordedBaseline shouldContain "\"declaration\": \"$recorded\""
    failure
}

private fun baselineArchitecture(constraints: LayoutDirectoryScope.() -> Unit): Architecture = architecture {
    files = wholeTree()
    baseline()
    "domain".group {
        "UseCase".role {
            layout {
                "gradlew".file()
                "katachi-baseline.json".file()
                "src" {
                    "*.kt".file()
                    constraints()
                }
            }
        }
    }
}

private fun KatachiArchitectureAssertionError.rejectedDeclarations(): List<String?> =
    violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.declaration }

/** A rule of a backend of one's own that rejects `User.id` once it is deprecated. */
private fun deprecatedUserIds(subject: FileConstraintSubject): List<FileConstraintFailure> =
    subject.files.filter { File("${subject.projectRoot}/$it").readText().contains("@Deprecated") }
        .map { FileConstraintFailure(it, declaration = "User.id") }

private val EMPTY_BASELINE: String = """
    {
      "version": 1,
      "checks": {}
    }
""".trimIndent() + "\n"

private val MODELS_WITH_VAR_USER_ID: String = """
    package com.example

    class User {
        var id: String = ""
    }

    class Order {
        val id: String = ""
    }
""".trimIndent() + "\n"

private val MODELS_WITH_VAR_ORDER_ID: String = """
    package com.example

    class User {
        val id: String = ""
    }

    class Order {
        var id: String = ""
    }
""".trimIndent() + "\n"

private val SCREENS_WITH_INTERNAL_HOME_STATE: String = """
    package com.example

    class HomeScreen {
        internal class State
    }

    class DetailScreen {
        class State
    }
""".trimIndent() + "\n"

private val SCREENS_WITH_INTERNAL_DETAIL_STATE: String = """
    package com.example

    class HomeScreen {
        class State
    }

    class DetailScreen {
        internal class State
    }
""".trimIndent() + "\n"

private val USE_CASES_WITH_INTERNAL_GET_USER: String = """
    package com.example

    class GetUser {
        internal fun invoke() = Unit
    }

    class GetOrder {
        fun invoke() = Unit
    }
""".trimIndent() + "\n"

private val USE_CASES_WITH_INTERNAL_GET_ORDER: String = """
    package com.example

    class GetUser {
        fun invoke() = Unit
    }

    class GetOrder {
        internal fun invoke() = Unit
    }
""".trimIndent() + "\n"

private val USER_WITH_VAR_ID: String = """
    package com.example

    class User {
        var id: String = ""
    }
""".trimIndent() + "\n"

private val USER_WITH_DEPRECATED_ID: String = """
    package com.example

    class User {
        @Deprecated("use key")
        val id: String = ""
    }
""".trimIndent() + "\n"
