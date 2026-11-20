package me.tbsten.katachi.konture

import me.tbsten.katachi.dsl.FileConstraintRange
import me.tbsten.katachi.dsl.FileConstraintScope
import me.tbsten.katachi.konture.internal.KontureFileConstraint

/**
 * Declares a named constraint over the files the block around it matched, written with Konture's rules.
 *
 * The rules are written with `files { }`, `classes { }`, `functions { }` and `properties { }`.
 *
 * `this` is the name the report prints next to the role, so it is a sentence about the code
 * ("does not depend on Compose") rather than an identifier. Where the call is written decides
 * what it covers: directly on a role it covers the union of every `layout { }` that role
 * declares, inside a directory block it covers that block's subtree and nothing else, or only
 * the files that block declares directly with `scope = FileConstraintRange.DirectOnly`.
 *
 * The block is stored, not run. It runs when a check that evaluates constraints runs, which
 * means `architecture { }` still reads no files and parses no Kotlin. The rules run over the
 * covered `.kt` files alone, parsed without Konture's Gradle plugin, with the names in them
 * looked up among the classes of every `.kt` file the check walked, and each violation becomes
 * one report block pointing at one file.
 *
 * It can be written on a role or inside its `layout { }`, but not directly in `architecture { }`
 * or `group { }`: those hold roles, not files, so the call does not compile there.
 *
 * ## Example 1: one rule over everywhere the role lives
 * ```kt
 * "ViewModel".role {
 *     "Does not depend on Compose".konture {
 *         classes { should() notDependOnPackages "androidx.compose.." }
 *     }
 *     layout {
 *         "feature" / "*" / "commonMain".sourceSet / kotlin / "com/example/kmp/feature" / "*" /
 *             "*ViewModel".ktFile()
 *     }
 * }
 * ```
 *
 * ## Example 2: two rules in one block, on one module
 * ```kt
 * "UseCase".role {
 *     layout {
 *         "core/domain" {
 *             "is a plain public class".konture {
 *                 classes { should().bePublic() }
 *                 functions { that() haveName "invoke"; should().bePublic() }
 *             }
 *             mainSourceSet / kotlin / "com/example/core/domain" / "useCase" / "*UseCase".ktFile()
 *         }
 *     }
 * }
 * ```
 *
 * ## Example 3: a rule for one directory that its subdirectories do not inherit
 * ```kt
 * "Util".role {
 *     layout {
 *         "util" {
 *             "does not use the KSP API".konture(scope = FileConstraintRange.DirectOnly) {
 *                 classes { should() notDependOnPackages "com.google.devtools.ksp.." }
 *             }
 *             "*".ktFile()
 *             "ksp" { "*".ktFile() } // not covered
 *         }
 *     }
 * }
 * ```
 *
 * @param scope how far below the surrounding directory block it reaches:
 *   [FileConstraintRange.Subtree] (the default) or [FileConstraintRange.DirectOnly]. See
 *   [FileConstraintScope.fileConstraint].
 * @param block what to ask of the covered files. See [KontureConstraintScope].
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintNameException when the name is blank or holds
 *   a line break — it is printed on one line of a report block.
 * @throws me.tbsten.katachi.dsl.KatachiDuplicateFileConstraintNameException when the role holds
 *   another constraint of the same name.
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintWithoutLayoutException when the role it is
 *   written on declares no `layout { }`, so nothing could ever be covered.
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintDirectOnlyWithoutDirectoryException when
 *   `scope = FileConstraintRange.DirectOnly` is written outside a directory block.
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintDirectOnlyCoversNothingException when
 *   `scope = FileConstraintRange.DirectOnly` is written in a block that declares no file directly.
 * @featured
 */
context(fileConstraintScope: FileConstraintScope)
public fun String.konture(
    scope: FileConstraintRange = FileConstraintRange.Subtree,
    block: KontureConstraintScope.() -> Unit,
) {
    fileConstraintScope.fileConstraint(name = this, scope = scope, check = KontureFileConstraint(block))
}

/**
 * Declares an unnamed constraint over the files the block around it matched, written with
 * Konture's rules.
 *
 * Identical to the named form, named `konture`: the report prints `FileConstraint: "konture"` and a
 * baseline records its violations under that name. A role therefore holds one unnamed
 * `konture { }`. Worth it when the block is short enough to read as its own name, and when it is
 * the only `konture { }` of the role; a report that has to say which of three rules failed
 * reads better with a name.
 *
 * ## Example 1: the only rule of one directory
 * ```kt
 * "UseCase".role {
 *     layout {
 *         "core/domain/useCase" {
 *             konture { classes { should().beInternal() } }
 *             "*UseCase.kt".file()
 *         }
 *     }
 * }
 * ```
 *
 * @param scope how far below the surrounding directory block it reaches, as for the named form.
 * @param block what to ask of the covered files. See [KontureConstraintScope].
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintWithoutLayoutException when the role it is
 *   written on declares no `layout { }`, so nothing could ever be covered.
 * @throws me.tbsten.katachi.dsl.KatachiDuplicateFileConstraintNameException when the role holds
 *   another constraint named `konture`: a second unnamed `konture { }`, or one given that name.
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintDirectOnlyWithoutDirectoryException when
 *   `scope = FileConstraintRange.DirectOnly` is written outside a directory block.
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintDirectOnlyCoversNothingException when
 *   `scope = FileConstraintRange.DirectOnly` is written in a block that declares no file directly.
 */
context(fileConstraintScope: FileConstraintScope)
public fun konture(
    scope: FileConstraintRange = FileConstraintRange.Subtree,
    block: KontureConstraintScope.() -> Unit,
) {
    fileConstraintScope.fileConstraint(name = UNNAMED_NAME, scope = scope, check = KontureFileConstraint(block))
}

private const val UNNAMED_NAME: String = "konture"
