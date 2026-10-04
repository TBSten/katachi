package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintScope
import me.tbsten.katachi.konsist.declarations.internal.KtFileContentConstraint

/** The name the report prints next to the role for every `ktFileContent { }`. */
private const val KT_FILE_CONTENT_NAME: String = "ktFileContent"

/**
 * Declares what the role's Kotlin files may hold -- their package, their imports, the
 * declarations directly in them and the members of their classes -- written the way the file
 * itself is written.
 *
 * - **Only the kinds a line is written for are checked, each as an allow list.** Write an import
 *   line, and every import no line allows is a violation; write none, and imports are not looked
 *   at. The same goes for the package, the declarations, and the members of each class line
 *   given a `{ }`.
 * - **Nothing is special by default.** Private declarations and imports of the same role count
 *   like any other; open them up with `private.anythingElse()` or `` `import`("**") ``.
 * - **A name without `*` is required, one with `*` is optional**, as in `layout { }`;
 *   `.optional()` makes a line optional.
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
 * ## Example 1: the shape of a view model file
 * ```kt
 * "ViewModel" {
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
 *         annotated("HiltViewModel")
 *             .public.`class`(fileName).superclass("ViewModel") {
 *                 public.`val`("uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
 *                 public.`fun`("on*")().returns("Unit")
 *                 private.anythingElse()
 *             }
 *     }
 * }
 * ```
 *
 * ## Example 2: a use case interface and its default implementation
 * ```kt
 * "UseCase" {
 *     layout { "core/domain" / mainSourceSet / kotlin / "com/example/domain" / "*UseCase".ktFile() }
 *     ktFileContent {
 *         `interface`(fileName) {
 *             operator.`fun`("invoke")
 *         }
 *         `class`("Default$fileName").superclass(fileName) {
 *             override.`fun`("invoke")
 *             private.anythingElse()
 *         }
 *     }
 * }
 * ```
 *
 * @param block the lines, written in [KtFileContentScope].
 * @throws KatachiKtFileContentModifierException when a line's modifiers fit no declaration.
 * @throws KatachiKtFileContentPatternException when a name, path or type is not a pattern katachi reads.
 * @throws KatachiKtFileContentLineException when a line is written in a way that means nothing.
 * @throws me.tbsten.katachi.dsl.KatachiFileConstraintWithoutLayoutException when the role it is
 *   written on declares no `layout { }`, so nothing could ever be covered.
 */
@ExperimentalKatachiApi
context(fileConstraintScope: FileConstraintScope)
public fun ktFileContent(block: KtFileContentScope.() -> Unit) {
    val declaredAt = DeclarationSite.capture()
    val constraint = KtFileContentConstraint(block, declaredAt)
    constraint.validateAtDeclaration()
    fileConstraintScope.fileConstraint(name = KT_FILE_CONTENT_NAME, declaredAt = declaredAt, check = constraint)
}
