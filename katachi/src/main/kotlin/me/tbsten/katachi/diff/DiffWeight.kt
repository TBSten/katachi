package me.tbsten.katachi.diff

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.GroupScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.metadata

/**
 * How much a change in a group or a role counts in the heat of [DiffReport], written in the
 * definition itself.
 *
 * Absent when it was not written, and absent means 1. A role takes its own value, else the
 * nearest group's that has one. A weight passed to the report (`--arg weights=`, or
 * `katachi { processors { diff { weights } } }` in the build script) wins over every value
 * written here, so the definition holds the project's lasting judgement and a run can still
 * look at a change another way.
 *
 * ## Example 1: put a change to the definition itself at the top of the heat
 * ```kt
 * import me.tbsten.katachi.diff.diffWeight
 *
 * val projectArchitecture = architecture {
 *     "testing".group {
 *         "RoleDefinition".role {
 *             diffWeight = 10.0
 *             layout { "architecture-test" / testSourceSet / kotlin / "**" / "*Role".ktFile() }
 *         }
 *     }
 *     "domain".group {
 *         diffWeight = 2.0 // every role of the group, unless a role writes its own
 *         "UseCase".role { layout { "domain" / "*UseCase".ktFile() } }
 *     }
 * }
 * ```
 */
@ExperimentalKatachiApi
public val DiffWeight: MetadataKey<Double> = metadata()

/**
 * Sets [DiffWeight] on a group: a non-negative number, 1 when left out. Every role of the group
 * takes it, unless the role writes its own.
 *
 * Only a group and a role take a weight, so it cannot be written at the top of `architecture { }`
 * or on a layout entry, where [DiffReport] would not read it.
 *
 * A negative value or one that is not a number fails the report with
 * [KatachiInvalidDefinitionDiffWeightException], naming where it was written.
 *
 * ## Example 1: weigh every role of a group twice
 * ```kt
 * "domain".group {
 *     diffWeight = 2.0
 *     "UseCase".role { layout { "domain" / "*UseCase".ktFile() } }
 * }
 * ```
 */
@ExperimentalKatachiApi
public var GroupScope.diffWeight: Double? by DiffWeight

/**
 * Sets [DiffWeight] on a role: a non-negative number. Left out, the role takes the nearest group's,
 * else 1.
 *
 * A negative value or one that is not a number fails the report with
 * [KatachiInvalidDefinitionDiffWeightException], naming where it was written.
 *
 * ## Example 1: put a change to the definition itself at the top of the heat
 * ```kt
 * "RoleDefinition".role {
 *     diffWeight = 10.0
 *     layout { "architecture-test" / testSourceSet / kotlin / "**" / "*Role".ktFile() }
 * }
 * ```
 */
@ExperimentalKatachiApi
public var RoleScope.diffWeight: Double? by DiffWeight
