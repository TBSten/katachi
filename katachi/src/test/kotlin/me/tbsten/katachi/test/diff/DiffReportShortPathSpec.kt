package me.tbsten.katachi.test.diff

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.diff.DiffReport
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.processor.internal.process

// The short path of a file in the diff report: the levels a wildcard of the matching layout
// pattern stood for and the file name stay, every run of literal levels folds into one "…".
@OptIn(ExperimentalKatachiApi::class)
class DiffReportShortPathSpec : FreeSpec({
    val arch = architecture {
        files = wholeTree()
        "Hoge".role { layout { "hoge" / "src" / "kotlin" / "main" / "com" / "example" / "*" / "hoge" / "*" / "*Hoge.kt".file() } }
        "Screen".role { layout { "app" / "**" / "ui" / "*Screen.kt".file() } }
        "Api".role { layout { "lib" / "*" / "api" { anyFile() } } }
    }

    "固定の階層は … にまとまり、wildcard に当たった階層とファイル名だけが残る" {
        withShortPathRepository { repo ->
            val paths = listOf(
                "hoge/src/kotlin/main/com/example/fuga/hoge/fuga/FugaHoge.kt",
                "app/feature/home/ui/HomeScreen.kt",
                "app/ui/RootScreen.kt",
                "lib/core/api/Thing.kt",
            )
            paths.forEach { repo.write(it, "class A\n") }
            repo.commit("initial")
            paths.forEach { repo.write(it, "class A { val a = 1 }\n") }
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val summary = out.resolve("diff-report.md").readText()
            val byRole = out.resolve("diff-report.files-by-role.md").readText()
            val html = out.resolve("diff-report.html").readText()
            // Two `*` in the middle of the pattern, with literal levels before, between and after them.
            summary shouldContain "`…/fuga/…/fuga/FugaHoge.kt`"
            byRole shouldContain "`…/fuga/…/fuga/FugaHoge.kt`"
            // A `**` keeps every level it matched, and the literal level after it folds on its own.
            summary shouldContain "`…/feature/home/…/HomeScreen.kt`"
            // A `**` that matched no level leaves nothing to keep but the file name.
            summary shouldContain "`…/RootScreen.kt`"
            // anyFile() shortens by the directory pattern it sits under.
            summary shouldContain "`…/core/…/Thing.kt`"
            // The HTML keeps both spellings, so the Path control can switch between them.
            html shouldContain "data-full=\"hoge/src/kotlin/main/com/example/fuga/hoge/fuga/FugaHoge.kt\" data-short=\"…/fuga/…/fuga/FugaHoge.kt\""
        }
    }
})

private class ShortPathRepository(val dir: File) {
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

private inline fun withShortPathRepository(block: (ShortPathRepository) -> Unit) {
    val dir = Files.createTempDirectory("katachi-diff-short-path").toFile()
    try {
        val repo = ShortPathRepository(dir)
        repo.git("init", "-q")
        repo.write("gradlew", "")
        repo.write(".gitignore", "out/\n")
        block(repo)
    } finally {
        dir.deleteRecursively()
    }
}
