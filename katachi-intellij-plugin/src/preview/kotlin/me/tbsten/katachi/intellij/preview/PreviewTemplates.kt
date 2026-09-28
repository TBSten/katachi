package me.tbsten.katachi.intellij.preview

import me.tbsten.katachi.intellij.model.BranchModel
import me.tbsten.katachi.intellij.model.CapturePlace
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.FilePreviewModel
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateSummaryModel
import java.nio.file.Path
import java.time.Instant

/*
 * The templates the preview scenarios show: shaped after the samples (Repository with `withImpl`,
 * UseCase, Service) plus the edge cases of the spec. Only the preview uses them. One template one
 * file, as design draft section 6 has it.
 */

/** Every scenario renders "now" as this, so relative times ("10 minutes ago") stay fixed. */
internal val PREVIEW_NOW: Instant = Instant.parse("2026-09-27T10:00:00Z")

internal val PREVIEW_ROOT: Path = Path.of("/Users/dev/project")

internal fun previewModule(path: String = ":architecture-test", root: Path = PREVIEW_ROOT, rootName: String = "project") =
    KatachiModule(path, root.resolve(path.trimStart(':').replace(':', '/')), root, rootName)

internal fun str(name: String, default: String? = null) =
    ParameterModel.StringParam(name, "String", default, default == null, default ?: "\${$name}")

internal fun int(name: String, default: String? = null) =
    ParameterModel.IntParam(name, "Int", default, default == null, default ?: "0")

internal fun bool(name: String, default: String? = "true") =
    ParameterModel.BooleanParam(name, "Boolean", default, default == null, default ?: "true")

internal fun enum(name: String, values: List<String>, default: String? = null) =
    ParameterModel.EnumParam(name, "Kind", default, default == null, default ?: values.first(), values)

internal fun kt(dir: String, name: String, path: Boolean = true, captures: List<String> = emptyList(), parameters: List<String> = emptyList()) =
    FilePreviewModel(if (path) "$dir/$name" else "$dir/**/$name", name, if (path) "$dir/$name" else null, captures, parameters, "")

internal fun template(
    roleName: String,
    title: String?,
    parameters: List<ParameterModel>,
    files: List<FilePreviewModel>,
    branches: List<BranchModel> = emptyList(),
    summary: String? = null,
    id: String? = null,
    captures: List<ParameterModel.CaptureParam> = emptyList(),
): TemplateModel {
    val specifier = id?.let { "$roleName.$it" } ?: roleName
    val shownTitle = title ?: id ?: roleName
    return TemplateModel(
        TemplateSummaryModel(specifier, id, shownTitle, roleName, summary, parameters.map { it.name }, conflict = false, captures.map { it.name }),
        TemplateDetailModel(
            specifier,
            id,
            shownTitle,
            roleName,
            summary,
            parameters,
            files,
            branches,
            "./gradlew katachiTemplate --arg template=$specifier",
            captures,
        ),
    )
}

internal fun previewFailedTemplate(roleName: String, title: String?): TemplateModel =
    TemplateModel(TemplateSummaryModel(roleName, null, title ?: roleName, roleName, null, listOf("name"), conflict = true), detail = null)

/** A capture at one place: a `/` level of a file pattern, or with [module] a `*` of a module key. */
internal fun capture(name: String, pattern: String, position: Int, module: Boolean = false, segment: String = "\${$name}") = ParameterModel.CaptureParam(
    name,
    listOf(CapturePlace(if (module) CapturePlace.KIND_MODULE else CapturePlace.KIND_PATH, pattern, position, segment)),
)

private const val DATA_DIR = "data/src/main/kotlin/com/example/data/user"
private const val DOMAIN_DIR = "domain/src/main/kotlin/com/example/domain/user"
private const val UI_DIR = "ui/src/main/kotlin/com/example/ui/user"

internal val dataSource = template("data.DataSource", "データソース", listOf(str("name")), listOf(kt(DATA_DIR, "\${name}DataSource.kt")))

internal val repository = template(
    "data.Repository",
    "リポジトリ",
    listOf(str("name"), str("item", default = "String"), bool("withImpl"), str("implSuffix", default = "Impl")),
    listOf(kt(DATA_DIR, "\${name}Repository.kt")),
    branches = listOf(
        BranchModel("withImpl", "false", emptyList(), emptyList(), emptyList(), listOf("implSuffix")),
    ),
    summary = "データ層の Repository",
)

internal val useCase = template("domain.UseCase", "ユースケース", listOf(str("name")), listOf(kt(DOMAIN_DIR, "\${name}UseCase.kt")))

internal val service = template("domain.Service", "サービス", listOf(str("name")), listOf(kt(DOMAIN_DIR, "\${name}Service.kt")))

/** Every input type: String (required / `${name}` default), Int, enum without default, Boolean. */
internal val component = template(
    "ui.Component",
    "画面の部品",
    listOf(str("name"), str("label", default = "\${name}"), int("columns", default = "2"), enum("kind", listOf("Compose", "View", "Internal")), bool("preview")),
    listOf(kt(UI_DIR, "\${name}Component.kt")),
    branches = listOf(
        BranchModel("kind", "Internal", emptyList(), emptyList(), listOf(str("internalName", default = "\${name}Internal")), emptyList()),
        BranchModel("preview", "false", emptyList(), emptyList(), emptyList(), emptyList()),
    ),
    summary = "Compose の画面部品",
)

/** Two branches differing at once make the footer count approximate (E-09). */
internal val screen = template(
    "ui.Screen",
    "画面",
    listOf(str("name"), bool("withViewModel"), bool("withPreview")),
    listOf(kt(UI_DIR, "\${name}Screen.kt")),
    branches = listOf(
        BranchModel("withViewModel", "false", emptyList(), emptyList(), emptyList(), emptyList()),
        BranchModel("withPreview", "false", emptyList(), emptyList(), emptyList(), emptyList()),
    ),
)

/** A detail with no id, one file, no special counting: the plain per-template shape. */
internal val navigation = template("ui.Navigation", "ナビゲーション", listOf(str("name")), listOf(kt(UI_DIR, "\${name}Route.kt")))

/** A file whose target is a wildcard (E-27). */
internal val mapper = template("data.Mapper", "マッパー", listOf(str("name")), listOf(kt("data/src/main/kotlin", "\${name}Mapper.kt", path = false)))

/** katachi could not preview it: summary only (E-07). */
internal val brokenTemplate = previewFailedTemplate("data.Cache", "キャッシュ")

/** A parameter of a kind this plugin does not know (E-36). */
internal val futureTemplate = template(
    "data.Remote",
    "リモート",
    listOf(str("name"), ParameterModel.UnknownParam("timeout", "Duration", null, true, "1s", "DurationParameter")),
    listOf(kt(DATA_DIR, "\${name}Remote.kt")),
)

/** `name: Int` in another module: same name, other type, so it never links (E-15). */
internal val pagedList = template("ui.PagedList", "ページ付きリスト", listOf(int("name", default = "20")), listOf(kt(UI_DIR, "PagedList.kt")))

/** The sample's FeatureComponent: `:feature:*` named `feature` with a module key capture. */
internal val featureComponent = template(
    "feature.FeatureComponent",
    "画面の部品",
    listOf(str("name"), bool("withPreview")),
    listOf(kt("feature/\${feature}/src/main/kotlin/com/example/feature/\${feature}/component", "\${name}.kt", captures = listOf("feature"), parameters = listOf("name", "withPreview"))),
    summary = "1つの画面でしか使わない @Composable",
    captures = listOf(capture("feature", ":feature:*", 0, module = true)),
)

/** A directory level named with `capture("feature")`. */
internal val featureViewModel = template(
    "feature.ViewModel",
    "ViewModel",
    listOf(str("name")),
    listOf(kt("app/src/main/kotlin/com/example/feature/\${feature}", "\${name}ViewModel.kt", captures = listOf("feature"), parameters = listOf("name"))),
    captures = listOf(capture("feature", "app/src/main/kotlin/com/example/feature/*/*ViewModel.kt", 7)),
)

internal fun snapshot(module: KatachiModule, vararg templates: TemplateModel, loadedAt: Instant = PREVIEW_NOW.minusSeconds(600)) =
    DescriptionSnapshot(module, "0.3.0", templates.toList(), loadedAt)

internal fun idOf(module: KatachiModule, template: TemplateModel) = TemplateId(module.id, template.template)
