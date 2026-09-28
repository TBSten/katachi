package me.tbsten.katachi.test.template

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.test.check.architectureOf

/**
 * `capture(...)` values fill in a template's path the same way for any random combination of
 * directory depth and value -- pinned against a hand-built expected path, the way
 * [me.tbsten.katachi.test.dsl.LayoutCapturePropertySpec] pins `capture()` against `*`. Uses a
 * seeded [kotlin.random.Random] loop rather than `io.kotest.property`, which is not a dependency
 * of `:katachi` (see [me.tbsten.katachi.test.dsl.RandomLayoutSpecSupport]).
 */
class TemplateCapturePlacementPropertySpec : FreeSpec({
    val iterations = System.getenv("KATACHI_PBT_ITERATIONS")?.toIntOrNull() ?: 200
    val seed = System.getenv("KATACHI_PBT_SEED")?.toLongOrNull() ?: 20260928L

    val names = listOf("a", "b", "c", "d", "e")
    val values = listOf("home", "settings", "x", "profile-edit", "v2")

    "capture を1〜3階層に置いた木は、どんな値でも手で組み立てたパスと一致する" {
        val random = Random(seed)
        repeat(iterations) { trial ->
            val depth = random.nextInt(1, 4)
            val captureNames = names.shuffled(random).take(depth)
            val chosenValues = captureNames.associateWith { values.random(random) }

            val arch = architectureOf {
                "domain".group {
                    "Trial" {
                        layout {
                            // A single key may hold several `/`-levels at once (`chainUnder`
                            // splits it), so the whole chain -- captures included -- is built as
                            // one string, without needing the `/` operator at all.
                            val key = "root/" + captureNames.joinToString("/") { capture(it) } + "/File.kt"
                            key.file().template {
                                captureNames.joinToString(",") { captureValue(it) }
                            }
                        }
                    }
                }
            }

            val expectedPath = ("root/" + captureNames.joinToString("/") { chosenValues.getValue(it) } + "/File.kt")
            val expectedContent = captureNames.joinToString(",") { chosenValues.getValue(it) }

            withClue("trial=$trial captureNames=$captureNames values=$chosenValues") {
                arch.generated("Trial", chosenValues) shouldBe mapOf(expectedPath to expectedContent)
            }
        }
    }
})
