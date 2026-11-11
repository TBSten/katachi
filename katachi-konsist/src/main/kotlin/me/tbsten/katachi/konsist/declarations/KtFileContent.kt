package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintScope
import me.tbsten.katachi.konsist.declarations.internal.KtFileContentConstraint

private const val KT_FILE_CONTENT_NAME: String = "ktFileContent"

/**
 * Declares what the role's Kotlin files may hold, written the way the file itself is written.
 *
 * It covers their package, their imports, the declarations directly in them and the members of
 * their classes.
 *
 * - **Nothing is allowed that no line allows (deny by default).** An empty `ktFileContent { }`
 *   rejects the file's package declaration, every import and every declaration. The package, the
 *   imports, the declarations and the members of each class line are each an allow list of the
 *   lines written for them; one no line is written for allows nothing, and a class line without
 *   `{ }` allows no member, as an empty `{ }` does. Open a kind up with `` `package`("**") ``
 *   (which a file declaring no package passes too), `` `import`("**") ``, `allow(anythingElse)`,
 *   or `{ allow(anythingElse) }` on a class line.
 * - **Nothing is special by default.** Private declarations and imports of the same role count
 *   like any other; open them up with `allow(private, anythingElse)` or `` `import`("**") ``.
 * - **A line's modifiers are the declaration's, as its Kotlin source writes them.**
 *   `` allow(`fun`, "execute") `` does not match `suspend fun execute`; `public` and `final` count as
 *   written, and a modifier that may be there or not is written `suspend.optional()`.
 *   Annotations, `allow(..., anythingElse)` and `not.allow(...)` ask only for what they write.
 * - **`allow(...)` lets a declaration be, `required(...)` asks for it.** A required line with no
 *   pattern for its name asks for that name; a pattern (`*`, `{A,B}` and the like) asks for at
 *   least one declaration it matches. An import line only allows.
 * - **The block runs once per file**, so [DeclarationListScope.fileName] and
 *   [DeclarationListScope.captureValue] are that file's own `String`s.
 *
 * It is checked by `FileConstraintCheck()`, like `konsist { }`, and covers what a constraint
 * written in the same place covers: on the role, every file of the role. A role holds one
 * `ktFileContent { }`.
 *
 * The block is also run once while the definition is read, with `fileName` set to `Sample` and
 * `captureValue("x")` to `<x>`, so that a line no declaration can match fails at the line. Anything
 * else the block throws with those stand-ins is left for the check, which runs it with real values.
 *
 * ## One block per role
 * A role holds one `ktFileContent { }`; a second one is refused both by the check and by the
 * role's page ([KatachiKtFileContentDuplicateException]). Rules that differ by directory are
 * written as separate roles, each with its own `layout { }` and its own block, rather than as
 * several blocks on one role. Give the block a name with the `"...".ktFileContent { }` form when
 * the report should say which shape failed.
 *
 * ## Example 1: the shape of a view model file
 * ```kt
 * "ViewModel".role {
 *     layout {
 *         "feature" / capture("feature") / mainSourceSet / kotlin / "**" / "*ViewModel".ktFile()
 *     }
 *     ktFileContent {
 *         `package`("com.example.feature.**")
 *
 *         `import`("kotlin.**")
 *         `import`("kotlinx.coroutines.**")
 *         `import`("androidx.**")
 *         `import`("com.example.domain.**")
 *         `import`("dagger.hilt.**")
 *         `import`("javax.inject.**")
 *
 *         required(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
 *             required(public, `val`, "uiState").type("StateFlow<${fileName.replace("ViewModel", "UiState")}>")
 *             allow(public, `fun`, "on*")().returns("Unit")
 *             allow(private, anythingElse)
 *         }
 *     }
 * }
 * ```
 *
 * ## Example 2: a use case interface and its default implementation
 * ```kt
 * "UseCase".role {
 *     layout { "core/domain" / mainSourceSet / kotlin / "com/example/domain" / "*UseCase".ktFile() }
 *     ktFileContent {
 *         `package`("com.example.domain")
 *         `import`("javax.inject.**")
 *
 *         required(`interface`, fileName) {
 *             required(operator, `fun`, "invoke")
 *         }
 *         required(`class`, "Default$fileName").superclass(fileName) {
 *             required(override, `fun`, "invoke")
 *             allow(private, anythingElse)
 *         }
 *     }
 * }
 * ```
 *
 * @param block the lines, written in [KtFileContentScope].
 * @throws KatachiKtFileContentModifierException when a line's modifiers fit no declaration.
 * @throws KatachiKtFileContentPatternException when a name, path or type is not a pattern katachi reads.
 * @throws KatachiKtFileContentLineException when a line is written in a way that means nothing,
 *   including a value of the vocabulary (a modifier, an annotation, a kind, `anythingElse`) that has
 *   not been passed to `allow(...)` or `required(...)` when the evaluation of the block ends.
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintWithoutLayoutException when the role it is
 *   written on declares no `layout { }`, so nothing could ever be covered.
 */
@ExperimentalKatachiApi
context(fileConstraintScope: FileConstraintScope)
public fun ktFileContent(block: KtFileContentScope.() -> Unit) {
    declareKtFileContent(fileConstraintScope, KT_FILE_CONTENT_NAME, block, DeclarationSite.capture())
}

/**
 * [ktFileContent] with a name for the block, printed in the report next to the role.
 *
 * `this` is a sentence about the shape ("the shape of a view model"), not an identifier, as with
 * `"...".konsist { }`.
 *
 * The report prints it as `<name> (ktFileContent)` next to the role, and so does the role's page, so
 * that a role's several constraints can be told apart and the reader still sees what kind of
 * check it is. Everything else is as for the unnamed form, including that a role holds one block.
 *
 * ## Example 1: name the shape of a view model file
 * ```kt
 * "ViewModel".role {
 *     layout { "feature" / "**" / "*ViewModel".ktFile() }
 *     "the shape of a view model".ktFileContent {
 *         `package`("**")
 *         `import`("**")
 *         allow(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
 *             allow(anythingElse)
 *         }
 *     }
 * }
 * ```
 *
 * @param block the lines, written in [KtFileContentScope].
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintNameException when the name is blank or holds
 *   a line break — it is printed on one line of a report block.
 * @throws KatachiKtFileContentModifierException when a line's modifiers fit no declaration.
 * @throws KatachiKtFileContentPatternException when a name, path or type is not a pattern katachi reads.
 * @throws KatachiKtFileContentLineException when a line is written in a way that means nothing,
 *   including a value of the vocabulary (a modifier, an annotation, a kind, `anythingElse`) that has
 *   not been passed to `allow(...)` or `required(...)` when the evaluation of the block ends.
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintWithoutLayoutException when the role it is
 *   written on declares no `layout { }`, so nothing could ever be covered.
 */
@ExperimentalKatachiApi
context(fileConstraintScope: FileConstraintScope)
public fun String.ktFileContent(block: KtFileContentScope.() -> Unit) {
    declareKtFileContent(fileConstraintScope, namedAfter(this), block, DeclarationSite.capture())
}

// A refused name is passed on without the suffix so the exception shows the name as written.
private fun namedAfter(name: String): String = if (isRefusedName(name)) name else "$name ($KT_FILE_CONTENT_NAME)"

// TODO: a copy of the rule `fileConstraint` applies to a name (blank, or holding a line break), kept
//  here so that a name is refused before the block is evaluated, as `"...".konsist { }` does. Drop it
//  once :katachi publishes that judgement.
private fun isRefusedName(name: String): Boolean = name.isBlank() || name.any { it == '\n' || it == '\r' }

private fun declareKtFileContent(
    scope: FileConstraintScope,
    name: String,
    block: KtFileContentScope.() -> Unit,
    declaredAt: DeclarationSite,
) {
    val constraint = KtFileContentConstraint(block, declaredAt)
    // A refused name is thrown by fileConstraint below, before the block would be evaluated.
    if (!isRefusedName(name)) constraint.validateAtDeclaration()
    scope.fileConstraint(name = name, declaredAt = declaredAt, check = constraint)
}
