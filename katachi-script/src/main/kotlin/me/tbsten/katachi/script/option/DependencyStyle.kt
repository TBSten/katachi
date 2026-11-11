// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.option

import me.tbsten.katachi.script.catalog
import me.tbsten.katachi.script.literal

/**
 * How `katachi scaffold --deps` writes the dependencies of the verification module.
 *
 * The top-level [catalog] and [literal] are the same values, so a script can write them bare.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.scaffold.deps(catalog)
 * katachi.scaffold.deps(DependencyStyle.Catalog) // the same
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class DependencyStyle(public val value: String) {
    /** Adds the libraries to `gradle/libs.versions.toml` and refers to them as `libs.katachi` (`catalog`). */
    Catalog("catalog"),

    /** Writes the coordinates into `build.gradle.kts` (`literal`). */
    Literal("literal"),
}
