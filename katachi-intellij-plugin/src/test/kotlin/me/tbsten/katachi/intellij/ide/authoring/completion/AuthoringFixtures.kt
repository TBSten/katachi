package me.tbsten.katachi.intellij.ide.authoring.completion

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateRef
import me.tbsten.katachi.intellij.model.TemplateSetModel
import me.tbsten.katachi.intellij.testing.branch
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template

/**
 * The definition the completion tests script: a `ui.Screen` template with the captures `feature` (path), `name` (module) and
 * `nameOf`, a `title` parameter and a branch that adds `withState`, and a `feature.FeatureViewModel` template. The screen's
 * `.template { }` is written in `ScreenRole.kt`.
 *
 * ```kotlin
 * service.emit(DefinitionState.Ready(listOf(AuthoringFixtures.snapshotWithSiteAt(2)), Freshness.Fresh))
 * ```
 */
internal object AuthoringFixtures {
    val screen: TemplateModel = template(
        "ui.Screen",
        parameters = listOf(stringParam("title")),
        captures = listOf(capture("feature"), capture("name", module = true), capture("nameOf")),
        branches = listOf(branch("kind", "A", addedParameters = listOf(stringParam("withState")))),
    )

    val viewModel: TemplateModel = template(
        "feature.FeatureViewModel",
        parameters = listOf(stringParam("withState"), stringParam("title")),
        captures = listOf(capture("feature"), capture("layer")),
    )

    /** Two templates of one role, told apart by their ids: the specifiers `data.Repository.a` and `data.Repository.b`. */
    val repositoryA: TemplateModel = template("data.Repository", id = "a", parameters = listOf(stringParam("title")), captures = listOf(capture("aOnly")))
    val repositoryB: TemplateModel = template("data.Repository", id = "b", parameters = listOf(stringParam("title")), captures = listOf(capture("bOnly")))

    /** A template set's row, which sits among the templates of the list but can be no member (`feature.NewFeature`). */
    val templateSet: TemplateModel = template("feature.NewFeature", parameters = listOf(stringParam("setParameter")))
        .copy(set = TemplateSetModel("NewFeature", emptyList()))

    val all: List<TemplateModel> = listOf(screen, viewModel, repositoryA, repositoryB, templateSet)

    /** The definition as the last Gradle run read it: the screen template written at [line] of `ScreenRole.kt`. */
    fun snapshotWithSiteAt(line: Int): DefinitionSnapshot = snapshotWithSites(screen to line)

    /** The definition with each of [sites] (a template and the line of its `.template { }` in `ScreenRole.kt`) in the layout of a role. */
    fun snapshotWithSites(vararg sites: Pair<TemplateModel, Int>): DefinitionSnapshot {
        val roles = sites.map { (template, line) ->
            val ref = TemplateRef(template.template, template.summary.id, null, DeclarationSiteRef("ScreenRole.kt", line))
            roleModel(template.roleName, layoutEntry("src/*${template.title}.kt").copy(template = ref))
        }
        return definitionSnapshot(definitionModel(*roles.toTypedArray()), module(":arch")).copy(templates = all)
    }

    /** [text] in a function: a definition's statements are not top level, so its first line is line 2. */
    fun inFunction(text: String): String = "fun definitions() {\n$text\n}\n"
}
