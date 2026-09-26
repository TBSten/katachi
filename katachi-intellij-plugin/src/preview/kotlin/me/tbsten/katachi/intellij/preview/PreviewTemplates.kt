package me.tbsten.katachi.intellij.preview

import me.tbsten.katachi.intellij.model.BranchModel
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
 * UseCase, Service) plus the edge cases of the spec. Only the preview uses them.
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

internal fun kt(dir: String, name: String, path: Boolean = true) =
    FilePreviewModel(name, if (path) "$dir/$name" else null, if (path) emptyList() else listOf("$dir/**/$name"), "")

internal fun template(
    roleName: String,
    title: String?,
    parameters: List<ParameterModel>,
    files: List<FilePreviewModel>,
    branches: List<BranchModel> = emptyList(),
    summary: String? = null,
    fileCount: Int? = files.size,
) = TemplateModel(
    TemplateSummaryModel(roleName, title, summary, parameters.map { it.name }, fileCount),
    TemplateDetailModel(roleName, title, summary, parameters, files, branches, "./gradlew katachiTemplate --arg roleName=$roleName"),
)

private const val DATA_DIR = "data/src/main/kotlin/com/example/data/user"
private const val DOMAIN_DIR = "domain/src/main/kotlin/com/example/domain/user"
private const val UI_DIR = "ui/src/main/kotlin/com/example/ui/user"

internal val dataSource = template("data/DataSource", "データソース", listOf(str("name")), listOf(kt(DATA_DIR, "\${name}DataSource.kt")))

internal val repository = template(
    "data/Repository",
    "リポジトリ",
    listOf(str("name"), str("item", default = "String"), bool("withImpl"), str("implSuffix", default = "Impl")),
    listOf(kt(DATA_DIR, "\${name}Repository.kt"), kt(DATA_DIR, "\${name}Repository\${implSuffix}.kt")),
    branches = listOf(
        BranchModel("withImpl", "false", emptyList(), listOf("\${name}Repository\${implSuffix}.kt"), emptyList(), listOf("implSuffix")),
    ),
    summary = "データ層の Repository と、その実装",
)

internal val useCase = template(
    "domain/UseCase",
    "ユースケース",
    listOf(str("name")),
    listOf(kt(DOMAIN_DIR, "\${name}UseCase.kt"), kt(DOMAIN_DIR, "\${name}UseCaseImpl.kt")),
)

internal val service = template("domain/Service", "サービス", listOf(str("name")), listOf(kt(DOMAIN_DIR, "\${name}Service.kt")))

/** Every input type: String (required / `${name}` default), Int, enum without default, Boolean. */
internal val component = template(
    "ui/Component",
    "画面の部品",
    listOf(str("name"), str("label", default = "\${name}"), int("columns", default = "2"), enum("kind", listOf("Compose", "View", "Internal")), bool("preview")),
    listOf(kt(UI_DIR, "\${name}Component.kt")),
    branches = listOf(
        BranchModel("kind", "Internal", listOf("\${name}Internal.kt"), emptyList(), listOf(str("internalName", default = "\${name}Internal")), emptyList()),
        BranchModel("preview", "false", emptyList(), emptyList(), emptyList(), emptyList()),
    ),
    summary = "Compose の画面部品",
)

/** Two branches differing at once make the footer count approximate (E-09). */
internal val screen = template(
    "ui/Screen",
    "画面",
    listOf(str("name"), bool("withViewModel"), bool("withPreview")),
    listOf(kt(UI_DIR, "\${name}Screen.kt"), kt(UI_DIR, "\${name}ViewModel.kt"), kt(UI_DIR, "\${name}Preview.kt")),
    branches = listOf(
        BranchModel("withViewModel", "false", emptyList(), listOf("\${name}ViewModel.kt"), emptyList(), emptyList()),
        BranchModel("withPreview", "false", emptyList(), listOf("\${name}Preview.kt"), emptyList(), emptyList()),
    ),
)

/** A detail whose summary has no count: the footer shows "N+" (E-09). */
internal val navigation = template("ui/Navigation", "ナビゲーション", listOf(str("name")), listOf(kt(UI_DIR, "\${name}Route.kt")), fileCount = null)

/** A file whose target is a wildcard (E-27). */
internal val mapper = template("data/Mapper", "マッパー", listOf(str("name")), listOf(kt("data/src/main/kotlin", "\${name}Mapper.kt", path = false)))

/** katachi could not preview it: summary only (E-07). */
internal val brokenTemplate = TemplateModel(TemplateSummaryModel("data/Cache", "キャッシュ", null, listOf("name"), null), detail = null)

/** A parameter of a kind this plugin does not know (E-36). */
internal val futureTemplate = template(
    "data/Remote",
    "リモート",
    listOf(str("name"), ParameterModel.UnknownParam("timeout", "Duration", null, true, "1s", "DurationParameter")),
    listOf(kt(DATA_DIR, "\${name}Remote.kt")),
)

/** `name: Int` in another module: same name, other type, so it never links (E-15). */
internal val pagedList = template("ui/PagedList", "ページ付きリスト", listOf(int("name", default = "20")), listOf(kt(UI_DIR, "PagedList.kt")))

internal fun snapshot(module: KatachiModule, vararg templates: TemplateModel, loadedAt: Instant = PREVIEW_NOW.minusSeconds(600)) =
    DescriptionSnapshot(module, "0.3.0", templates.toList(), loadedAt)

internal fun idOf(module: KatachiModule, template: TemplateModel) = TemplateId(module.id, template.roleName)
