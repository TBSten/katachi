package me.tbsten.katachi.intellij.preview

import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.LinkUi
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogUiState
import me.tbsten.katachi.intellij.ui.dialog.ListNoticeUi
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings
import me.tbsten.katachi.intellij.ui.dialog.TargetNoticeUi

/*
 * The generate dialog's states, counted from what its UI state can be (`GenerateDialogUiState`) times
 * the state of its parts: the window (smallest / usual / largest), the number of parameters, the
 * definition select, the existing-file notice (3 kinds), the validation errors, the Generate button
 * that cannot be pressed, an opened select box, a long path, Japanese text wrapping and a select box
 * with one template. Each state is one scenario, drawn in light and dark.
 */

/** The dialog's window: the smallest it can be resized to, the usual one, and a large one. */
internal enum class DialogSize(val width: Int, val height: Int) {
    Min(480, 380),
    Standard(640, 480),
    Max(960, 720),
}

/** Which select box a scenario opens before drawing. */
internal enum class OpenSelect { Template, Definition }

internal data class DialogScenario(
    val name: String,
    val ui: GenerateDialogUiState,
    val size: DialogSize = DialogSize.Standard,
    val english: Boolean = false,
    val open: OpenSelect? = null,
)

internal val japaneseDialogStrings by lazy { PropertiesGenerateDialogStrings.japanese() }
internal val englishDialogStrings by lazy { PropertiesGenerateDialogStrings.english() }

private val forComponent = idOf(arch, component)

private fun fieldId(name: String) = FieldId(forComponent, name)

private fun text(
    name: String,
    value: String = "",
    required: Boolean = false,
    placeholder: String? = null,
    error: String? = null,
    number: Boolean = false,
    hint: String? = null,
) = FieldUi.Text(fieldId(name), name, required, value, placeholder, number, error, LinkUi.None, isMultiline = null, multilineTooltip = null, hint = hint)

private fun flag(name: String, checked: Boolean = true) = FieldUi.Bool(fieldId(name), name, checked, LinkUi.None)

private fun choice(name: String, options: List<String>, selected: Int = -1, error: String? = null) =
    FieldUi.Choice(fieldId(name), name, isRequired = true, options, selected, "選んでください", error, LinkUi.None)

private val templateTitles = listOf("画面の部品", "画面", "リポジトリ", "ユースケース", "サービス", "ナビゲーション")

private val basePath = "ui/src/main/kotlin/com/example/ui/user"

private fun state(
    fields: List<FieldUi> = listOf(text("name", required = true)),
    path: String = "$basePath/\${name}Component.kt",
    notice: TargetNoticeUi? = null,
    canGenerate: Boolean = false,
    templates: List<String> = templateTitles,
    selected: Int = 0,
    definitions: List<String> = emptyList(),
    summary: String? = "Compose の画面部品",
    listNotice: ListNoticeUi? = null,
    refusal: String? = null,
) = GenerateDialogUiState(
    templateOptions = templates,
    selectedTemplate = selected,
    definitionOptions = definitions,
    summary = summary,
    fields = fields,
    targetPath = path,
    targetNotice = notice,
    listNotice = listNotice,
    refusal = refusal,
    canGenerate = canGenerate,
)

/** Twelve parameters, one of every kind, the first one still empty (the cursor starts there). */
private val manyFields = listOf(
    text("name", required = true),
    text("label", placeholder = "\${name}"),
    text("columns", value = "2", number = true),
    choice("kind", listOf("Compose", "View", "Internal")),
    flag("preview"),
    text("item", placeholder = "String"),
    text("implSuffix", placeholder = "Impl"),
    flag("withImpl", checked = false),
    text("description", placeholder = "画面の説明"),
    text("packageSuffix", placeholder = "\${name}"),
    text("tag", placeholder = "ui"),
    text("owner", placeholder = "team-a"),
)

private val filledMany = manyFields.map { field ->
    when (field.id.parameterName) {
        "name" -> (field as FieldUi.Text).copy(value = "UserProfile")
        "kind" -> (field as FieldUi.Choice).copy(selectedIndex = 0)
        else -> field
    }
}

private val filledPath = "$basePath/UserProfileComponent.kt"

private const val LONG_ROOT = "feature/notification-settings/src/commonMain/kotlin/com/example/katachi/sample/feature/notificationsettings/presentation/detail/component"

private val longPath = "$LONG_ROOT/\${name}NotificationSettingsDetailComponent.kt"

private val captureField = text(
    "feature",
    value = "home",
    required = true,
    hint = "生成先 feature/<feature>/src/main/kotlin/com/example/feature/<feature>/component の <feature> に入るディレクトリ名",
)

/** Every state. Names say what the state is; the sizes are the window (Min / Standard / Max). */
internal val dialogScenarios: List<DialogScenario> = buildList {
    // The window x the number of parameters (0 / many).
    for (size in DialogSize.entries) {
        val tag = size.name.lowercase()
        add(DialogScenario("size-$tag-params-none", state(fields = emptyList(), path = "$basePath/Navigation.kt", canGenerate = true, notice = TargetNoticeUi.WillCreate, summary = null), size))
        add(DialogScenario("size-$tag-params-many", state(fields = manyFields), size))
    }
    add(DialogScenario("params-many-filled", state(fields = filledMany, path = filledPath, canGenerate = true, notice = TargetNoticeUi.WillCreate)))
    add(DialogScenario("params-all-types", state(fields = filledMany.take(8), path = filledPath, canGenerate = true, notice = TargetNoticeUi.WillCreate), DialogSize.Max))

    // The definition select box (two definitions) and the template select with one template.
    val definitions = listOf(":architecture-test", ":architecture-test-features")
    add(DialogScenario("definitions-two", state(definitions = definitions)))
    add(DialogScenario("definitions-two-min", state(definitions = definitions), DialogSize.Min))
    add(DialogScenario("template-single", state(templates = listOf("画面の部品"))))
    add(DialogScenario("template-single-min", state(templates = listOf("画面の部品")), DialogSize.Min))

    // The existing-file notice (issue 6): the three kinds.
    val filled = listOf(text("name", value = "UserProfile", required = true))
    add(DialogScenario("notice-will-create", state(filled, filledPath, TargetNoticeUi.WillCreate, canGenerate = true)))
    add(DialogScenario("notice-will-overwrite-empty", state(filled, filledPath, TargetNoticeUi.WillOverwriteEmpty, canGenerate = true)))
    add(DialogScenario("notice-cannot-overwrite", state(filled, filledPath, TargetNoticeUi.CannotOverwrite, canGenerate = false)))
    add(DialogScenario("notice-cannot-overwrite-min", state(filled, filledPath, TargetNoticeUi.CannotOverwrite, canGenerate = false), DialogSize.Min))

    // Validation errors, and Generate that cannot be pressed with nothing wrong shown yet.
    add(DialogScenario("cannot-generate-initial", state()))
    add(DialogScenario("validation-required", state(listOf(text("name", required = true, error = "入力してください")))))
    add(
        DialogScenario(
            "validation-several",
            state(
                fields = listOf(
                    text("name", required = true, error = "入力してください"),
                    text("columns", value = "abc", number = true, error = "整数で入力してください（-2147483648〜2147483647）"),
                    choice("kind", listOf("Compose", "View", "Internal"), error = "入力してください"),
                ),
            ),
        ),
    )
    add(DialogScenario("validation-capture", state(listOf(captureField.copy(value = "home/list", error = "1階層の名前にしてください（/ と \\ は使えません）")))))

    // An opened select box.
    add(DialogScenario("select-open-template", state(filled, filledPath, TargetNoticeUi.WillCreate, canGenerate = true), open = OpenSelect.Template))
    add(DialogScenario("select-open-template-min", state(filled, filledPath, TargetNoticeUi.WillCreate, canGenerate = true), DialogSize.Min, open = OpenSelect.Template))
    add(DialogScenario("select-open-definition", state(filled, filledPath, TargetNoticeUi.WillCreate, canGenerate = true, definitions = definitions), open = OpenSelect.Definition))

    // Long paths, and captures with their notes.
    add(DialogScenario("long-path", state(filled, longPath, TargetNoticeUi.WillCreate, canGenerate = false)))
    add(DialogScenario("long-path-min", state(filled, longPath, TargetNoticeUi.WillCreate, canGenerate = false), DialogSize.Min))
    add(DialogScenario("capture-seeded", state(listOf(captureField, text("name", required = true)), "feature/home/src/main/kotlin/com/example/feature/home/component/\${name}.kt")))
    add(DialogScenario("capture-seeded-min", state(listOf(captureField, text("name", required = true)), "feature/home/src/main/kotlin/com/example/feature/home/component/\${name}.kt"), DialogSize.Min))
    add(
        DialogScenario(
            "field-collapsed",
            state(
                listOf(
                    text("name", value = "UserProfile", required = true),
                    flag("withImpl", checked = false),
                    FieldUi.Collapsed(fieldId("implSuffix"), japaneseDialogStrings.collapsedField("implSuffix", "withImpl", "true")),
                ),
                filledPath,
                TargetNoticeUi.WillCreate,
                canGenerate = true,
            ),
        ),
    )

    // Japanese wrapping in the smallest window: long notices and a refusal.
    val gone = ListNoticeUi.TemplateReplaced("画面の部品")
    add(DialogScenario("japanese-wrap-min", state(listOf(captureField.copy(value = "")), listNotice = gone, refusal = "生成先に中身のあるファイルがあります。別の名前にしてください"), DialogSize.Min))
    add(DialogScenario("list-notice-template-replaced", state(filled, filledPath, TargetNoticeUi.WillCreate, canGenerate = true, listNotice = gone)))
    add(DialogScenario("list-notice-no-candidates", state(fields = emptyList(), path = "", templates = emptyList(), selected = -1, summary = null, listNotice = ListNoticeUi.NoCandidates)))
    add(DialogScenario("refused", state(filled, filledPath, TargetNoticeUi.WillCreate, canGenerate = true, refusal = "生成先に中身のあるファイルがあります")))

    // English texts.
    add(DialogScenario("english-standard", state(filled.map { it.copy(value = "") }, notice = TargetNoticeUi.WillCreate), english = true))
    add(DialogScenario("english-min", state(manyFields.take(3), definitions = definitions, notice = TargetNoticeUi.WillOverwriteEmpty), DialogSize.Min, english = true))
}
