@file:Suppress("DEPRECATION")
package me.tbsten.katachi.test.template

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.template

/**
 * `synthetic-template-set.json`: template sets next to the templates they gather.
 *
 * The roles are the members; none of them is a set. `feature.FeatureScreen` and
 * `feature.FeatureViewModel` write below `src/main/kotlin/feature/<name>/`; `shared.Logger` is in
 * another group and takes a `tag`; `ui.Screen` sits below the module capture `:feature:*`, so its
 * file has no `path` and a `modulePlacements` entry in each feature module (`feature/home` and
 * `feature/settings`, as `IdePluginSyntheticJsonSpec`'s project has them).
 *
 * The sets (the order of `templateSets[]` is the order they are declared in):
 * - `Starter` (declared at the root, so `groupName` is `null`): `feature.FeatureScreen` and
 *   `feature.FeatureViewModel` with one `name`. Japanese title.
 * - `feature.NewFeature` (in a group): the two above plus `shared.Logger` (a member from another
 *   group, `tag` fed from `name` through an expression) and `ui.Screen` (the module capture:
 *   `feature` and `name`). `withState` is a Boolean parameter passed on as it is.
 * - `feature.Broken`: passes `feature.FeatureScreen` an `arg` it does not take, so its preview
 *   fails: `conflict: true`, no entry in `templateSetDetails`, and a `TemplateSetPreviewFailed`
 *   problem in the definition JSON.
 */
internal fun syntheticTemplateSetArchitecture(): Architecture = architecture {
    "Starter".templateSet {
        title = "はじめの一式"
        val name by stringParameter()
        template("feature.FeatureScreen") { arg("name", name) }
        template("feature.FeatureViewModel") { arg("name", name) }
    }
    "feature".group {
        "FeatureScreen".role {
            layout {
                "src/main/kotlin/feature/${capture("name")}" / "${capture("name")}Screen.kt".file().template {
                    "fun ${captureValue("name")}Screen() {}\n"
                }
            }
        }
        "FeatureViewModel".role {
            layout {
                "src/main/kotlin/feature/${capture("name")}" / "${capture("name")}ViewModel.kt".file().template {
                    val withState by booleanParameter(default = true)
                    "class ${captureValue("name")}ViewModel(val state: Boolean = $withState)\n"
                }
            }
        }
        "NewFeature".templateSet {
            title = "新しい機能"
            summary = "画面・ViewModel・ログの印・ページを一度に作る"
            val feature by stringParameter()
            val name by stringParameter()
            val withState by booleanParameter(default = true)
            template("feature.FeatureScreen") { arg("name", name) }
            template("feature.FeatureViewModel") {
                arg("name", name)
                arg("withState", withState)
            }
            template("shared.Logger") { arg("tag", "${name}Tag") }
            template("ui.Screen") {
                arg("feature", feature)
                arg("name", name)
            }
        }
        "Broken".templateSet {
            title = "壊れた組"
            template("feature.FeatureScreen") { arg("typo", "x") }
        }
    }
    "shared".group {
        "Logger".role {
            layout {
                "src/main/kotlin/shared" / "Logger.kt".file().template {
                    val tag by stringParameter()
                    "val TAG = \"$tag\"\n"
                }
            }
        }
    }
    "ui".group {
        "Screen".role {
            layout {
                ":feature:${capture("feature")}".module {
                    "src/main/kotlin" / "${capture("name")}Page.kt".file().template {
                        "fun ${captureValue("name")}Page() {}\n"
                    }
                }
            }
        }
    }
}
