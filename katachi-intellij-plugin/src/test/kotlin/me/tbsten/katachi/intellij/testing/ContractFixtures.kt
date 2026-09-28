package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.data.json.parseTemplateDescriptionJson
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateModel
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The contract goldens under `src/test/resources/contract/`.
 *
 * `json/arch-a.json`, `json/arch-b.json` and `output/` (other than `output/real-jvm-*.log`) are
 * hand-written from the contract in `.local/ide-plugin-impl/plan.md`. The real ones, left unedited
 * apart from the project root, are `json/sample-{jvm,android,kmp}.json` and
 * `json/sample-{jvm,android,kmp}-with-captures.json` (`katachiInternalTemplatesJson` of each
 * sample) and `output/real-jvm-*.log` (Gradle's whole console output of sample/jvm's
 * `katachiTemplate` and `katachiInternalTemplatesJson`); refresh them from the samples whenever
 * katachi's output changes (design draft's step 10, stage I-2: run
 * `:architecture-test:katachiInternalTemplatesJson` under each sample, and `katachiTemplate` under
 * sample/jvm for the `real-jvm-*` scenarios). Output files write the project root as
 * `file:///__ROOT__`.
 *
 * `json/sample-{jvm,android,kmp}.json` hold one template each -- a `capture()` reaches almost every
 * file's name now, so unlike before the template-per-file redesign there is no longer a real
 * template without one; `arch-a.json` stands in for that shape instead (hand-written, no `captures`
 * key at all). `json/sample-*-with-captures.json` are the whole, unedited outputs of the sample;
 * `json/capture.json` is hand-written for the shapes the samples lack (several captures, one name
 * in two places, a template without the key).
 */
internal object ContractFixtures {
    const val ROOT_TOKEN: String = "file:///__ROOT__"

    fun text(path: String): String {
        val stream = ContractFixtures::class.java.getResourceAsStream("/contract/$path")
            ?: throw IllegalArgumentException("No test resource /contract/$path")
        return stream.use { String(it.readBytes(), Charsets.UTF_8) }
    }

    fun json(name: String): String = text("json/$name.json")

    fun templates(name: String): List<TemplateModel> = parseTemplateDescriptionJson(json(name), Paths.get("/fixture/$name.json"))

    /** The output lines of `output/<name>.log`, the root token replaced by [root]. */
    fun outputLines(name: String, root: Path): List<String> {
        val rootUri = root.toUri().toString().removeSuffix("/")
        return text("output/$name.log").replace(ROOT_TOKEN, rootUri).split("\n").let { lines ->
            // A file ending in a line break has no line after it; a cut-off file keeps its last line.
            if (lines.last().isEmpty()) lines.dropLast(1) else lines
        }
    }

    fun exitCode(name: String): Int = text("output/$name.exit").trim().toInt()
}

internal val ROOT: Path = Paths.get("/work/project")

internal fun module(gradlePath: String = ":arch-a", root: Path = ROOT, rootName: String = "project"): KatachiModule =
    KatachiModule(gradlePath, root.resolve(gradlePath.trim(':').replace(':', '/')), root, rootName)

/** The rows of a fixture JSON as if loaded from [module]. */
internal fun rowsOf(fixture: String, module: KatachiModule = module()): List<ModuleTemplate> =
    ContractFixtures.templates(fixture).map { ModuleTemplate(module, it) }

internal fun List<ModuleTemplate>.row(roleName: String): ModuleTemplate =
    firstOrNull { it.template.roleName == roleName } ?: throw IllegalArgumentException("No row $roleName")

/** [row], but by the complete specifier: needed once a role has more than one template. */
internal fun List<ModuleTemplate>.rowOfTemplate(template: String): ModuleTemplate =
    firstOrNull { it.template.template == template } ?: throw IllegalArgumentException("No row $template")
