package me.tbsten.katachi.test.diff

import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.diff.DiffReport
import me.tbsten.katachi.diff.KatachiDiffReportIoException
import me.tbsten.katachi.diff.KatachiInvalidDefinitionDiffWeightException
import me.tbsten.katachi.diff.KatachiInvalidDiffWeightException
import me.tbsten.katachi.diff.diffWeight
import me.tbsten.katachi.diff.internal.ChangeStatus
import me.tbsten.katachi.diff.internal.DiffData
import me.tbsten.katachi.diff.internal.DiffRange
import me.tbsten.katachi.diff.internal.DiffReportModel
import me.tbsten.katachi.diff.internal.FileChange
import me.tbsten.katachi.diff.internal.GitHubRepository
import me.tbsten.katachi.diff.internal.PullRequest
import me.tbsten.katachi.diff.internal.diffRecord
import me.tbsten.katachi.diff.internal.diffReportData
import me.tbsten.katachi.diff.internal.renderJson
import me.tbsten.katachi.diff.internal.renderYaml
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.processor.internal.process
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper

@OptIn(ExperimentalKatachiApi::class)
class DiffReportDataSpec : FreeSpec({
    val schema = diffReportSchema()
    val arch = architecture {
        files = wholeTree()
        "Presentation".group {
            title = "画面"
            "ViewModel".role {
                diffWeight = 2.0
                layout { "app" / "**" / "*ViewModel.kt".file() }
            }
            "Screen".role { layout { "app" / "**" / "*Screen.kt".file() } }
        }
        "Root".role {
            layout {
                "gradlew".file()
                ".gitignore".file()
                "generated".ignore()
            }
        }
    }

    "group → role → ファイルの木を、同じ中身の JSON と YAML に書く" {
        withDataRepository { repo ->
            repo.write("app/home/HomeViewModel.kt", "class HomeViewModel\n")
            repo.write("app/home/OldScreen.kt", "fun Screen() {\n    val a = 1\n    val b = 2\n}\n")
            repo.commit("initial")
            repo.write("app/home/HomeViewModel.kt", "class HomeViewModel {\n    val a = 1\n}\n")
            repo.git("mv", "app/home/OldScreen.kt", "app/home/HomeScreen.kt")
            repo.write("notes.txt", "no role\n")
            repo.write("generated/Api.kt", "a\nb\n")
            repo.commit("change")
            val before = repo.git("rev-parse", "HEAD~1").trim()
            val after = repo.git("rev-parse", "HEAD").trim()

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(
                    before = "HEAD~1",
                    after = "HEAD",
                    markdown = false,
                    html = false,
                    jsonOutputFile = out.resolve("diff-report.json").path,
                    yamlOutputFile = out.resolve("diff-report.yaml").path,
                ),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            out.list().orEmpty().sorted() shouldBe listOf("diff-report.json", "diff-report.yaml")
            val jsonText = out.resolve("diff-report.json").readText()
            val yamlText = out.resolve("diff-report.yaml").readText()
            schema.problemsIn(jsonText, InputFormat.JSON).shouldBeEmpty()
            schema.problemsIn(yamlText, InputFormat.YAML).shouldBeEmpty()
            val json = parseJson(jsonText)
            parseYaml(yamlText) shouldBe json

            json.at("schemaVersion") shouldBe 1
            json.at("range") shouldBe mapOf(
                "label" to "HEAD~1 (${before.take(7)})..HEAD (${after.take(7)})",
                "before" to "HEAD~1",
                "after" to "HEAD",
                "beforeSha" to before,
                "afterSha" to after,
                "pullRequest" to null,
                "repositoryUrl" to null,
                "projectPath" to "",
            )
            json.at("summary") shouldBe mapOf(
                "fileCount" to 4,
                "added" to 6,
                "deleted" to 1,
                "roleCount" to 3,
                "changedRoleCount" to 2,
                "changedRoles" to listOf("Presentation.ViewModel", "Presentation.Screen"),
                "noRoleFileCount" to 1,
                "noRoleDeletedFileCount" to 0,
                "ignoredFileCount" to 1,
                "fullImportanceLines" to 400,
                "maxImportance" to 5,
            )
            // The definition's order: the top level roles, then the groups. An unchanged role is there too.
            json.at("nodes", 0).without("files") shouldBe mapOf(
                "type" to "role", "name" to "Root", "qualifiedName" to "Root", "title" to null, "changed" to false,
                "fileCount" to 0, "added" to 0, "deleted" to 0, "score" to 0.0, "importance" to 0, "weight" to 1.0,
            )
            json.at("nodes", 0, "files") shouldBe emptyList<Any?>()
            json.at("nodes", 1).without("children") shouldBe mapOf(
                "type" to "group", "name" to "Presentation", "qualifiedName" to "Presentation", "title" to "画面", "changed" to true,
                "fileCount" to 2, "added" to 3, "deleted" to 1, "score" to 8.0, "importance" to 1,
            )
            json.at("nodes", 1, "children", 0).without("files") shouldBe mapOf(
                "type" to "role", "name" to "ViewModel", "qualifiedName" to "Presentation.ViewModel", "title" to null, "changed" to true,
                "fileCount" to 1, "added" to 3, "deleted" to 1, "score" to 8.0, "importance" to 1, "weight" to 2.0,
            )
            json.at("nodes", 1, "children", 0, "files", 0) shouldBe mapOf(
                "path" to "app/home/HomeViewModel.kt",
                "previousPath" to null,
                "status" to "modified",
                "binary" to false,
                "added" to 3,
                "deleted" to 1,
                "score" to 8.0,
                "importance" to 1,
                "githubUrl" to null,
                "gitDiffCommand" to "git diff $before..$after -- app/home/HomeViewModel.kt",
            )
            json.at("nodes", 1, "children", 1, "files", 0, "path") shouldBe "app/home/HomeScreen.kt"
            json.at("nodes", 1, "children", 1, "files", 0, "previousPath") shouldBe "app/home/OldScreen.kt"
            json.at("nodes", 1, "children", 1, "files", 0, "status") shouldBe "renamed"
            json.at("noRole").without("files") shouldBe mapOf("fileCount" to 1, "added" to 1, "deleted" to 0)
            json.at("noRole", "files", 0, "path") shouldBe "notes.txt"
            json.at("noRole", "files", 0, "status") shouldBe "added"
            json.at("noRoleDeleted") shouldBe mapOf("fileCount" to 0, "added" to 0, "deleted" to 0, "files" to emptyList<Any?>())
            json.at("ignored").without("files") shouldBe mapOf("fileCount" to 1, "added" to 2, "deleted" to 0)
            json.at("ignored", "files", 0, "path") shouldBe "generated/Api.kt"

            // Keys plain, text always quoted, so a SHA of digits alone or a role named "No" stays text.
            yamlText shouldContain "schemaVersion: 1\nrange:\n  label: \"HEAD~1 (${before.take(7)})..HEAD (${after.take(7)})\"\n"
            yamlText shouldContain "  afterSha: \"$after\"\n  pullRequest: null\n"
            yamlText shouldContain "  - type: \"group\"\n    name: \"Presentation\"\n    qualifiedName: \"Presentation\"\n    title: \"画面\"\n    changed: true\n"
            yamlText shouldContain "          - path: \"app/home/HomeViewModel.kt\"\n            previousPath: null\n            status: \"modified\"\n"
            yamlText shouldContain "    score: 8.0\n    importance: 1\n    children:\n      - type: \"role\"\n"
            yamlText shouldContain "    weight: 1.0\n    files: []\n"
        }
    }

    "既定では 4 つの形式をすべて書き、json=false・yaml=false で JSON と YAML を止められる" {
        withDataRepository { repo ->
            repo.commit("initial")
            repo.write("app/home/HomeViewModel.kt", "a\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(
                    before = "HEAD~1",
                    after = "HEAD",
                    markdownOutputFile = out.resolve("diff-report.md").path,
                    htmlOutputFile = out.resolve("diff-report.html").path,
                    jsonOutputFile = out.resolve("diff-report.json").path,
                    yamlOutputFile = out.resolve("diff-report.yaml").path,
                ),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            out.list().orEmpty().sorted() shouldBe
                listOf("diff-report.html", "diff-report.json", "diff-report.md", "diff-report.yaml")

            val markdownOnly = repo.dir.resolve("markdown-only")
            arch.process(
                DiffReport,
                DiffReport.Args(
                    before = "HEAD~1",
                    after = "HEAD",
                    html = false,
                    json = false,
                    yaml = false,
                    markdownOutputFile = markdownOnly.resolve("diff-report.md").path,
                    jsonOutputFile = markdownOnly.resolve("diff-report.json").path,
                    yamlOutputFile = markdownOnly.resolve("diff-report.yaml").path,
                ),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            markdownOnly.list().orEmpty().sorted() shouldBe listOf("diff-report.md")
        }
    }

    "JSON と YAML は maxFiles で切らず、作業ツリーの未追跡ファイルも全部載せる" {
        withDataRepository { repo ->
            repo.commit("initial")
            for (index in 1..4) repo.write("app/f$index/F${index}ViewModel.kt", "a\n".repeat(index))

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(
                    maxFiles = 1,
                    html = false,
                    yaml = false,
                    markdownOutputFile = out.resolve("diff-report.md").path,
                    jsonOutputFile = out.resolve("diff-report.json").path,
                ),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            out.resolve("diff-report.md").readText() shouldContain "Showing 1 of 4 files (maxFiles=1)."
            val json = parseJson(out.resolve("diff-report.json").readText())
            json.at("range", "after") shouldBe null
            json.at("range", "afterSha") shouldBe null
            // Most diff lines first, as the other formats list them.
            json.at("nodes", 1, "children", 0, "files").paths() shouldBe
                listOf("app/f4/F4ViewModel.kt", "app/f3/F3ViewModel.kt", "app/f2/F2ViewModel.kt", "app/f1/F1ViewModel.kt")
            json.at("nodes", 1, "children", 0, "files", 0, "status") shouldBe "untracked"
            json.at("nodes", 1, "children", 0, "files", 0, "gitDiffCommand") shouldBe
                "git diff --no-index -- /dev/null app/f4/F4ViewModel.kt"
        }
    }

    "プロジェクトがリポジトリの一部なら projectPath にその場所を書き、path はプロジェクトからのまま" {
        withDataRepository { repo ->
            repo.write("project/gradlew", "")
            repo.write("project/app/home/HomeViewModel.kt", "a\n")
            repo.commit("initial")
            repo.write("project/app/home/HomeViewModel.kt", "a\nb\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(
                    before = "HEAD~1",
                    after = "HEAD",
                    markdown = false,
                    html = false,
                    yaml = false,
                    jsonOutputFile = out.resolve("diff-report.json").path,
                ),
                RealFileSystem(repo.dir.resolve("project")),
            ).getOrThrow()

            val json = parseJson(out.resolve("diff-report.json").readText())
            json.at("range", "projectPath") shouldBe "project"
            json.at("nodes", 1, "children", 0, "files", 0, "path") shouldBe "app/home/HomeViewModel.kt"
            "${json.at("nodes", 1, "children", 0, "files", 0, "gitDiffCommand")}" shouldContain "-- project/app/home/HomeViewModel.kt"
        }
    }

    "PR のレポートは pullRequest と、ファイルごとの GitHub のリンクを持つ" {
        // `pr=` goes through `gh pr view`, which a test repository cannot answer, so the range is built by hand.
        val base = "1".repeat(40)
        val head = "2".repeat(40)
        val noRole = FileChange("notes.txt", previousPath = null, added = 1, deleted = 0, binary = false, status = ChangeStatus.Added)
        val image = FileChange("docs/logo.png", previousPath = null, added = 0, deleted = 0, binary = true, status = ChangeStatus.Modified)
        val data = diffReportData(
            DiffReportModel(
                range = DiffRange(
                    before = base,
                    after = head,
                    separator = "...",
                    beforeSha = base,
                    afterSha = head,
                    pullRequest = PullRequest(number = 7, url = "https://github.com/example/app/pull/7"),
                    github = GitHubRepository(owner = "example", name = "app"),
                    repositoryPrefix = "",
                ),
                nodes = emptyList(),
                unassigned = listOf(noRole),
                changes = listOf(noRole, image),
                ignored = listOf(image),
            ),
        )

        schema.problemsIn(data.renderJson(), InputFormat.JSON).shouldBeEmpty()
        schema.problemsIn(data.renderYaml(), InputFormat.YAML).shouldBeEmpty()
        val json = parseJson(data.renderJson())
        parseYaml(data.renderYaml()) shouldBe json
        json.at("range", "label") shouldBe "PR #7"
        json.at("range", "pullRequest") shouldBe mapOf("number" to 7, "url" to "https://github.com/example/app/pull/7")
        json.at("range", "repositoryUrl") shouldBe "https://github.com/example/app"
        json.at("nodes") shouldBe emptyList<Any?>()
        "${json.at("noRole", "files", 0, "githubUrl")}" shouldContain "https://github.com/example/app/pull/7/files#diff-"
        json.at("noRole", "files", 0, "gitDiffCommand") shouldBe "git diff $base...$head -- notes.txt"
        json.at("ignored", "files", 0, "binary") shouldBe true
        json.at("ignored", "files", 0, "added") shouldBe 0
    }

    "YAML は文字列を必ず引用符で囲み、別の値に読まれうる文字列も文字列のまま読める" {
        val texts = listOf(
            "ViewModel", "Presentation.ViewModel", "app/home/Home-View_Model.kt",
            "No", "on", "Y", "n", "yes", "OFF", "true", "False", "null", "NULL", "~",
            "", " ", " leading", "trailing ", "a: b", "a #b", "- a", "? a", "[a]", "{a}", "&a", "*a", "!a", "|", ">", "%a", "@a", "`a`",
            "12", "1.5", "0x1F", "1e3", "1_000", "1:30", "2026-11-16", ".inf", ".NaN", "-", "--", "...",
            "a\"b", "a\\b", "a'b", "a\nb", "a\tb", "a\rb", "\u0001", "a\u0085b", "a\u2028b", "\uFEFFa", "a\uFFFEb", "日本語", "…/home/Home.kt", "😀",
            "git diff 1..2 -- app/Home.kt",
        )
        val data = diffRecord {
            put("texts", texts.map(DiffData::Text))
            for ((index, text) in texts.withIndex()) put("k$index", text)
            put("No", "a key is text too")
            put("a b", "a key with a space")
        }

        val yaml = data.renderYaml()
        val parsed = parseYaml(yaml)
        parsed shouldBe parseJson(data.renderJson())
        parsed.at("texts") shouldBe texts
        parsed.at("No") shouldBe "a key is text too"
        yaml shouldContain "texts:\n  - \"ViewModel\"\n  - \"Presentation.ViewModel\"\n  - \"app/home/Home-View_Model.kt\"\n  - \"No\"\n"
        // A key is plain unless YAML could read it as something else.
        yaml shouldContain "\nk0: \"ViewModel\"\n"
        yaml shouldContain "\n\"No\": \"a key is text too\"\n\"a b\": \"a key with a space\"\n"
        yaml shouldNotContain "\u2028"
    }

    "YAML と JSON は、数値・空の入れ物・入れ子のリストを同じ値に書く" {
        val nothing: String? = null
        val data = diffRecord {
            put("int", 12)
            put("whole", 3.0)
            put("fraction", 0.5)
            put("large", 1.0E10)
            put("small", 1.0E-5)
            put("flag", true)
            put("nothing", nothing)
            put("emptyRecord", diffRecord { })
            put("emptyItems", emptyList())
            put("nested", listOf(DiffData.Items(listOf(DiffData.Integer(1), DiffData.Text("no"))), DiffData.Items(emptyList())))
            put("records", listOf(diffRecord { }, diffRecord { put("a", 1) }))
        }

        // No exponent: YAML 1.1 readers take "1.0E10" for text.
        data.renderJson() shouldContain "\"large\": 10000000000.0,"
        data.renderJson() shouldContain "\"small\": 0.000010,"
        data.renderYaml() shouldContain "whole: 3.0\nfraction: 0.5\nlarge: 10000000000.0\n"
        data.renderYaml() shouldContain "emptyRecord: {}\nemptyItems: []\nnested:\n  - [1, \"no\"]\n  - []\nrecords:\n  - {}\n  - a: 1\n"
        parseYaml(data.renderYaml()) shouldBe parseJson(data.renderJson())
        parseJson(data.renderJson()).at("whole") shouldBe 3.0
    }

    "JSON の書き出し先に書けなければ、その場所と別の出力先の渡し方を言って失敗する" {
        withDataRepository { repo ->
            repo.commit("initial")
            repo.write("blocker", "not a directory\n")
            val output = repo.dir.resolve("blocker/diff-report.json").path
            val failure = arch.process(
                DiffReport,
                DiffReport.Args(markdown = false, html = false, yaml = false, jsonOutputFile = output),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure as? KatachiDiffReportIoException)?.outputFile shouldBe output
            failure?.message.orEmpty() shouldContain "--arg jsonOutputFile="
        }
    }

    "無限大や NaN の重みは、JSON に書けない score になる前に失敗する" {
        withDataRepository { repo ->
            repo.commit("initial")
            for (weight in listOf("Infinity", "NaN")) {
                withClue(weight) {
                    val failure = arch.process(
                        DiffReport,
                        DiffReport.Args(weights = listOf("Root=$weight"), markdownOutputFile = repo.dir.resolve("out/diff-report.md").path),
                        RealFileSystem(repo.dir),
                    ).exceptionOrNull()
                    (failure is KatachiInvalidDiffWeightException) shouldBe true
                }
            }
            val infinite = architecture {
                files = wholeTree()
                "Root".role {
                    diffWeight = Double.POSITIVE_INFINITY
                    layout { "gradlew".file() }
                }
            }
            val failure = infinite.process(
                DiffReport,
                DiffReport.Args(markdownOutputFile = repo.dir.resolve("out/diff-report.md").path),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure as? KatachiInvalidDefinitionDiffWeightException)?.qualifiedName shouldBe "Root"
        }
    }
})

private fun diffReportSchema(): Schema {
    val text = checkNotNull(DiffReportDataSpec::class.java.classLoader.getResourceAsStream("diff/diff-report.schema.json")) {
        "diff/diff-report.schema.json is missing from the test resources"
    }.use { it.readBytes().toString(Charsets.UTF_8) }
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(text)
}

private fun Schema.problemsIn(text: String, format: InputFormat): List<String> = validate(text, format).map { it.toString() }

// Maps, lists and plain values, so the JSON and the YAML compare with `shouldBe`.
private fun parseJson(text: String): Any? = JsonMapper().readValue(text, Any::class.java)

private fun parseYaml(text: String): Any? = YAMLMapper().readValue(text, Any::class.java)

private fun Any?.at(vararg path: Any): Any? = path.fold(this) { node, key ->
    when (key) {
        is String -> (node as? Map<*, *>)?.get(key)
        is Int -> (node as? List<*>)?.getOrNull(key)
        else -> null
    }
}

private fun Any?.without(key: String): Map<*, *> = (this as? Map<*, *>).orEmpty() - key

private fun Any?.paths(): List<Any?> = (this as? List<*>).orEmpty().map { it.at("path") }

private class DataRepository(val dir: File) {
    fun write(path: String, content: String) {
        dir.resolve(path).apply { parentFile.mkdirs() }.writeText(content)
    }

    fun commit(message: String) {
        git("add", "-A")
        git("-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-q", "--allow-empty", "-m", message)
    }

    fun git(vararg args: String): String {
        val process = ProcessBuilder(listOf("git", *args)).directory(dir).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        if (process.waitFor() != 0) throw IllegalStateException("git ${args.joinToString(" ")} failed: $output")
        return output
    }
}

private inline fun withDataRepository(block: (DataRepository) -> Unit) {
    val dir = Files.createTempDirectory("katachi-diff-data").toFile()
    try {
        val repo = DataRepository(dir)
        repo.git("init", "-q")
        repo.write("gradlew", "")
        repo.write(".gitignore", "out/\n")
        block(repo)
    } finally {
        dir.deleteRecursively()
    }
}
