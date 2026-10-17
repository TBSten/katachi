package me.tbsten.katachi.test.template

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.TemplateSetScope
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.test.check.architectureOf

/**
 * A definition with the roles a template set gathers, and the set [setBlock] declares.
 *
 * - `feature.FeatureScreen`: capture `name`, no parameter. Writes `feature/<name>Screen.kt`.
 * - `feature.FeatureViewModel`: capture `name`, `withState` (Boolean, default `true`),
 *   `size` (Int, default `1`). Writes `feature/<name>ViewModel.kt`.
 * - `feature.FeatureTest`: capture `name`, `suffix` (String, **no default**). Writes
 *   `feature/<name>Test.kt`.
 * - `feature.Branchy`: capture `name`, and `extra` declared only when `mode` is `Extra`.
 *
 * The set is written in the group `feature` as `NewFeature`.
 */
internal fun templateSetArchitecture(setBlock: TemplateSetScope.() -> Unit): Architecture =
    architectureOf {
        "feature".group {
            "FeatureScreen".role {
                layout {
                    "feature" / "${capture("name")}Screen.kt".file().template { "screen ${captureValue("name")}" }
                }
            }
            "FeatureViewModel".role {
                layout {
                    "feature" / "${capture("name")}ViewModel.kt".file().template {
                        val withState by booleanParameter(default = true)
                        val size by intParameter(default = 1)
                        "vm ${captureValue("name")} state=$withState size=$size"
                    }
                }
            }
            "FeatureTest".role {
                layout {
                    "feature" / "${capture("name")}Test.kt".file().template {
                        val suffix by stringParameter()
                        "test ${captureValue("name")} $suffix"
                    }
                }
            }
            "Branchy".role {
                layout {
                    "feature" / "${capture("name")}Branchy.kt".file().template {
                        val mode by enumParameter(BranchMode.Plain)
                        if (mode == BranchMode.Extra) {
                            val extra by stringParameter(default = "e")
                            "branchy ${captureValue("name")} $mode $extra"
                        } else {
                            "branchy ${captureValue("name")} $mode"
                        }
                    }
                }
            }
            "NewFeature".templateSet(setBlock)
        }
    }

internal enum class BranchMode { Plain, Extra }
