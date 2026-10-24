@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.konsist.konsist

/**
 * The constraints this artifact adds resolve inside a `gradle { }` hook, whose receiver is
 * `GradleRoleScope` rather than a role block: `ktFileContent { }` and `"name".konsist { }` are
 * written on `FileConstraintScope` as a context parameter, and the hook is one.
 *
 * Pinned from outside `:katachi` on purpose: the declarations cannot be read back across the
 * module boundary, so what is observed is the run — a check that evaluates nothing reports
 * each constraint as `[UncheckedFileConstraint]` under `Gradle.BuildScript`, which is at once
 * proof that the calls compiled against the hook and that neither declaration was dropped.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.konsist.test` にしてはいけない。
 * captureDeclarationSite() が自分のフレームを読み飛ばし、宣言位置が kotest 内部を指す。
 */
class KonsistGradleHookSpec : FreeSpec({
    "buildScript { } の中の ktFileContent と konsist は BuildScript の制約として宣言される" {
        fixtureProject("build.gradle.kts" to "plugins { }\n") { root ->
            val projectArchitecture = architecture {
                files = wholeTree()
                gradle(requireWrapper = false) {
                    buildScript {
                        ktFileContent { required(`class`, fileName) { } }
                        "internal であること".konsist { classes().must { it.hasInternalModifier } }
                    }
                }
            }

            val unchecked = projectArchitecture.validate(RealFileSystem(root)).filterIsInstance<UncheckedFileConstraint>()
            unchecked.map { it.role.qualifiedName }.distinct() shouldBe listOf("Gradle.BuildScript")
            unchecked.map { it.reason }.distinct() shouldBe listOf(UncheckedFileConstraintReason.NotEvaluated)
            unchecked.size shouldBe 2
        }
    }
})
