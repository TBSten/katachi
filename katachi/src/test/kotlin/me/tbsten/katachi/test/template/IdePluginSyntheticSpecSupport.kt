package me.tbsten.katachi.test.template

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module

internal enum class SyntheticMode { Compact, Expanded }

internal enum class SyntheticStyle { Plain, Fancy, Minimal }

/**
 * `synthetic-structure.json`: one definition module that covers the shapes a template can take.
 *
 * The roles are declared going back and forth between depths (root, `a/Shallow`, `a/b/c/Deep`,
 * `a/b/Middle`, `other/...`, root again), but the JSON lists the root's roles first and then each
 * group depth-first: `AllTypes`, `Counter`, `a/Shallow`, `a/b/Middle`, `a/b/c/Deep`,
 * `other/Broken`, `other/Wildcard`.
 *
 * Which roleName shows what (the IDE plugin's tests rely on this table):
 * - `AllTypes` (root, depth 0): every parameter type with and without a default -- `name`,
 *   `label` (String), `enabled`, `verbose` (Boolean), `count`, `pageSize` (Int), `mode`,
 *   `fallbackMode` (enum `SyntheticMode`). Japanese title and a summary. One file.
 * - `a/Shallow` (depth 1): branches. `withImpl` (default true) removes `implSuffix` and a file
 *   when false; `withTest` (default false) adds `testName` and a file when true; `style` (enum
 *   `SyntheticStyle`, default Plain) adds `decoration` and a file at Fancy, and nothing at Minimal.
 * - `a/b/c/Deep` (depth 3): four files; `itemName` defaults to `${name}Item`, a default read
 *   from another parameter. No title, no summary.
 * - `a/b/Middle` (depth 2): declares a parameter but writes no file. katachi refuses a template
 *   that produces no file, so like `other/Broken` it is not in `details` and its `fileCount` is
 *   null. English title, no summary.
 * - `other/Broken`: throws for a placeholder, so it is not in `details` and its `fileCount` is null.
 * - `other/Wildcard`: its module is `:feature:*`, so the file's `path` is null and
 *   `unresolvedPatterns` holds the pattern.
 * - `Counter` (root again, declared last): `name` is an Int here, while it is a String in
 *   `AllTypes`, `a/Shallow`, `a/b/c/Deep` and `a/b/Middle` (same name, same type). Title only.
 *   A required Int previews as `0`, so its file is `Counter0.kt`.
 */
internal fun syntheticStructureArchitecture(): Architecture = architecture {
    "AllTypes" {
        title = "全部の型"
        summary = "string / boolean / int / enum を既定値あり・なしで1つずつ持つ"
        layout { "src/main/kotlin/allTypes" / "*.kt".file() }
        template {
            val name by stringParameter()
            val label by stringParameter(default = "ラベル")
            val enabled by booleanParameter()
            val verbose by booleanParameter(default = false)
            val count by intParameter()
            val pageSize by intParameter(default = 20)
            val mode by enumParameter(SyntheticMode.entries)
            val fallbackMode by enumParameter(default = SyntheticMode.Compact)
            file("$name.kt") {
                "// $label $enabled $verbose $count $pageSize ${mode.name} ${fallbackMode.name}\nclass $name\n"
            }
        }
    }
    "a".group {
        "Shallow" {
            title = "浅い"
            summary = "Boolean と enum の分岐で引数とファイルが増減する"
            layout { "src/main/kotlin/a" / "*.kt".file() }
            template {
                val name by stringParameter()
                val withImpl by booleanParameter(default = true)
                val withTest by booleanParameter(default = false)
                val style by enumParameter(default = SyntheticStyle.Plain)
                file("$name.kt") { "interface $name\n" }
                if (withImpl) {
                    val implSuffix by stringParameter(default = "Impl")
                    file("$name$implSuffix.kt") { "class $name$implSuffix : $name\n" }
                }
                if (withTest) {
                    val testName by stringParameter(default = "${name}Test")
                    file("$testName.kt") { "class $testName\n" }
                }
                if (style == SyntheticStyle.Fancy) {
                    val decoration by stringParameter(default = "*")
                    file("${name}Decoration.kt") { "// $decoration\n" }
                }
            }
        }
        "b".group {
            "c".group {
                "Deep" {
                    layout { "src/main/kotlin/a/b/c" / "*.kt".file() }
                    template {
                        val name by stringParameter()
                        val itemName by stringParameter(default = "${name}Item")
                        file("$name.kt") { "class $name\n" }
                        file("$itemName.kt") { "class $itemName\n" }
                        file("${name}Repository.kt") { "interface ${name}Repository\n" }
                        file("${name}ViewModel.kt") { "class ${name}ViewModel\n" }
                    }
                }
            }
            "Middle" {
                title = "Middle"
                layout { "src/main/kotlin/a/b" / "*.kt".file() }
                template {
                    val name by stringParameter()
                    check(name.isNotEmpty())
                }
            }
        }
    }
    "other".group {
        "Broken" {
            title = "壊れたテンプレート"
            layout { "src/main/kotlin/other" / "*.kt".file() }
            template {
                val name by stringParameter()
                // A placeholder is not a real name, and this template refuses it.
                if (name.startsWith("$")) throw IllegalStateException("no preview for $name")
                file("$name.kt") { "class $name\n" }
            }
        }
        "Wildcard" {
            summary = "生成先のモジュールが決まらない"
            layout { ":feature:*".module { "src/main/kotlin" / "*Screen.kt".file() } }
            template {
                val name by stringParameter()
                file("${name}Screen.kt") { "fun ${name}Screen() {}\n" }
            }
        }
    }
    "Counter" {
        title = "カウンター"
        layout { "src/main/kotlin/counter" / "*.kt".file() }
        template {
            val name by intParameter()
            file("Counter$name.kt") { "const val COUNTER: Int = $name\n" }
        }
    }
}

/** How many templates `synthetic-many.json` holds. */
internal const val SYNTHETIC_MANY_COUNT: Int = 300

/**
 * `synthetic-many.json`: [SYNTHETIC_MANY_COUNT] templates, to see a long list stay usable.
 *
 * Templates `T000`..`T299` are spread over ten groups by `index % 10`: `g0`..`g6` sit at the root,
 * and `g7`, `g8`, `g9` are nested as `nested/g7`, `nested/deeper/g8` and `nested/deeper/g9`. So the
 * roleNames look like `g3/T013` or `nested/deeper/g9/T299`. Each template has one to three
 * parameters by `index % 3`: `name` (String), then `enabled` (Boolean, default true), then
 * `size` (Int, default = index). Even indices have a Japanese title; every fifth has a summary.
 * No template has branches. The JSON lists them group by group (`g0/T000`, `g0/T010`, ...,
 * `nested/deeper/g9/T299`).
 */
internal fun syntheticManyArchitecture(): Architecture = architecture {
    val byGroup = (0 until SYNTHETIC_MANY_COUNT).groupBy { it % 10 }
    for (group in 0..6) {
        "g$group".group { manyRoles(byGroup.getValue(group)) }
    }
    "nested".group {
        "g7".group { manyRoles(byGroup.getValue(7)) }
        "deeper".group {
            "g8".group { manyRoles(byGroup.getValue(8)) }
            "g9".group { manyRoles(byGroup.getValue(9)) }
        }
    }
}

private fun DeclarationContainerScope.manyRoles(indices: List<Int>) {
    for (index in indices) {
        val id = index.toString().padStart(3, '0')
        "T$id" {
            if (index % 2 == 0) title = "テンプレート $id"
            if (index % 5 == 0) summary = "$id 番目の合成テンプレート"
            layout { "src/main/kotlin/many" / "*.kt".file() }
            template {
                val name by stringParameter()
                val parts = mutableListOf(name)
                if (index % 3 >= 1) {
                    val enabled by booleanParameter(default = true)
                    parts += enabled.toString()
                }
                if (index % 3 == 2) {
                    val size by intParameter(default = index)
                    parts += size.toString()
                }
                file("${name}T$id.kt") { "// ${parts.joinToString(" ")}\n" }
            }
        }
    }
}

/**
 * `synthetic-second.json`: a second definition module, to put next to `synthetic-structure.json`.
 *
 * - `AllTypes`: the same roleName as in structure, with the same parameter names and types.
 * - `a/Shallow`: the same roleName as in structure, but `withImpl` is a String here (a Boolean
 *   there) and `style` is gone; `name` keeps its type.
 * - `Counter`: the same roleName, and `name` is an Int in both.
 * - `second/OnlyHere`, `second/AlsoOnlyHere` and `SecondRoot` (root): only in this module.
 *
 * JSON order: `AllTypes`, `SecondRoot`, `Counter`, `a/Shallow`, `second/OnlyHere`,
 * `second/AlsoOnlyHere`.
 */
internal fun syntheticSecondArchitecture(): Architecture = architecture {
    "AllTypes" {
        title = "全部の型（2つ目）"
        layout { "src/main/kotlin/allTypes" / "*.kt".file() }
        template {
            val name by stringParameter()
            val label by stringParameter(default = "ラベル")
            val enabled by booleanParameter()
            val verbose by booleanParameter(default = false)
            val count by intParameter()
            val pageSize by intParameter(default = 20)
            val mode by enumParameter(SyntheticMode.entries)
            val fallbackMode by enumParameter(default = SyntheticMode.Compact)
            file("${name}Second.kt") {
                "// $label $enabled $verbose $count $pageSize ${mode.name} ${fallbackMode.name}\nclass ${name}Second\n"
            }
        }
    }
    "a".group {
        "Shallow" {
            title = "浅い（2つ目）"
            layout { "src/main/kotlin/a" / "*.kt".file() }
            template {
                val name by stringParameter()
                val withImpl by stringParameter(default = "yes")
                file("$name.kt") { "// $withImpl\ninterface $name\n" }
            }
        }
    }
    "second".group {
        "OnlyHere" {
            title = "2つ目だけ"
            layout { "src/main/kotlin/second" / "*.kt".file() }
            template {
                val name by stringParameter()
                file("${name}OnlyHere.kt") { "class ${name}OnlyHere\n" }
            }
        }
        "AlsoOnlyHere" {
            layout { "src/main/kotlin/second" / "*.kt".file() }
            template {
                val name by stringParameter()
                val count by intParameter(default = 3)
                file("${name}AlsoOnlyHere.kt") { "val ${name}Count: Int = $count\n" }
            }
        }
    }
    "SecondRoot" {
        summary = "2つ目のルート直下"
        layout { "src/main/kotlin/secondRoot" / "*.kt".file() }
        template {
            val name by stringParameter()
            file("${name}SecondRoot.kt") { "class ${name}SecondRoot\n" }
        }
    }
    "Counter" {
        title = "カウンター"
        layout { "src/main/kotlin/counter" / "*.kt".file() }
        template {
            val name by intParameter()
            file("Counter$name.kt") { "const val COUNTER: Int = $name\n" }
        }
    }
}
