package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * `docs`: fetches the published docs into the working directory and prints the path. The docs come
 * from a `file://` directory through `KATACHI_DOCS`, the same fetcher as https (KATACHI-266).
 */
class DocsCommandSpec : FreeSpec({
    "--topics は作業用ディレクトリが無くても名前と見出しを出す" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "docs", "--topics"))
        run.status shouldBe 0
        run.out shouldContain "faq\tFAQ"
        run.out shouldContain "kt-file-content\tktFileContent"
    }

    "全文を取って作業用ディレクトリに置き、stdout にはパスだけを出す" {
        val fixture = DocsFixture()

        val run = fixture.run("agent", "docs")

        run.status shouldBe 0
        val dest = fixture.workdir.resolve("tmp/cache/llms-full.txt")
        run.out.trim() shouldBe dest.toString()
        dest.readText() shouldBe FULL
        run.err shouldContain "取得元: ${fixture.base}/llms-full.txt"
        fixture.workdir.resolve("tmp/cache/MANIFEST").readText() shouldContain "${fixture.base}/llms-full.txt"
    }

    "取得済みなら取り直さず、--refresh で取り直す" {
        val fixture = DocsFixture()
        fixture.run("agent", "docs")
        fixture.docs.resolve("llms-full.txt").writeText("# Updated\n")

        val cached = fixture.run("agent", "docs")
        cached.err shouldContain "取得済みのものを使いました。取り直すなら --refresh"
        fixture.workdir.resolve("tmp/cache/llms-full.txt").readText() shouldBe FULL

        fixture.run("agent", "docs", "--refresh").status shouldBe 0
        fixture.workdir.resolve("tmp/cache/llms-full.txt").readText() shouldBe "# Updated\n"
    }

    "--small と --api は別のファイルを取る" {
        val fixture = DocsFixture()

        fixture.run("agent", "docs", "--small").out.trim() shouldBe fixture.workdir.resolve("tmp/cache/llms-small.txt").toString()
        fixture.run("agent", "docs", "--api").out.trim() shouldBe fixture.workdir.resolve("tmp/cache/api-llms.txt").toString()
        val module = fixture.run("agent", "docs", "--api", "katachi")

        module.status shouldBe 0
        Path(module.out.trim()).readText() shouldBe "katachi api\n"
    }

    "--topic はその見出しのページだけを切り出す（コードの中の # は見出しにしない）" {
        val fixture = DocsFixture()

        val run = fixture.run("agent", "docs", "--topic", "faq")

        run.status shouldBe 0
        val page = Path(run.out.trim()).readText()
        page shouldBe "# FAQ\n\nQ. why\n\n```sh\n# not a heading\n```\n\nA. because\n"
        run.err shouldContain "9 行（llms-full.txt の「FAQ」）"
    }

    "--topic は [AI-generated] の付いた見出しも拾う" {
        val fixture = DocsFixture()
        Path(fixture.run("agent", "docs", "--topic", "recipes").out.trim()).readText() shouldContain "Recipe body"
    }

    "知らない話題は使える名前を挙げて止まる" {
        val run = DocsFixture().run("agent", "docs", "--topic", "nothing")
        run.status shouldBe 1
        run.err shouldContain "docs --topic: 知らない名前です: nothing 。使える名前:\nmotivation\t"
    }

    "見出しが無ければ、取り直したばかりならコマンドの不具合、取得済みなら --refresh を案内する" {
        val fixture = DocsFixture()

        val fresh = fixture.run("agent", "docs", "--topic", "roadmap")
        fresh.status shouldBe 1
        fresh.err shouldContain "katachi コマンドの不具合です"
        fixture.workdir.resolve("tmp/cache/topics/roadmap.md").exists() shouldBe false

        val cached = fixture.run("agent", "docs", "--topic", "roadmap")
        cached.err shouldContain "--refresh を付けて取り直してください（katachi agent docs --topic roadmap --refresh）"
    }

    "古い版の写しを切り出したら止まる" {
        val run = DocsFixture().run("agent", "docs", "--topic", "git")
        run.status shouldBe 1
        run.err shouldContain "古い版（/0.2/ など）の写しでした"
    }

    "取れなければ手で置く場所を案内して止まる" {
        val fixture = DocsFixture()
        fixture.docs.resolve("llms-full.txt").toFile().delete()

        val run = fixture.run("agent", "docs")

        run.status shouldBe 1
        run.err shouldContain "ダウンロードに失敗しました: ${fixture.base}/llms-full.txt"
        run.err shouldContain "tmp/cache/llms-full.txt に置いてから同じコマンドをやり直してください。"
    }

    "作業用ディレクトリが無ければ init を案内して止まる" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "docs"))
        run.status shouldBe 1
        run.err shouldContain "先に 'katachi agent init' を実行してください"
    }
})

/** Two pages, the second with a heading inside a code fence; then an old version's copy of the Git page. */
private val FULL = """
    # FAQ

    Q. why

    ```sh
    # not a heading
    ```

    A. because
    # [AI-generated] Recipes
    Recipe body
    # Git
    See [the old page](/katachi/0.2/git).
""".trimIndent() + "\n"

private class DocsFixture {
    val docs: Path = createTempDirectory("katachi-docs-")
    val base: String = "file://$docs"
    val workdir: Path = workdirWith("""{"meta": {}, "steps": []}""")

    init {
        docs.resolve("llms-full.txt").writeText(FULL)
        docs.resolve("llms-small.txt").writeText("small\n")
        docs.resolve("api-docs").createDirectories().resolve("llms.txt").writeText("api index\n")
        docs.resolve("api-docs/katachi").createDirectories().resolve("llms-full.txt").writeText("katachi api\n")
    }

    fun run(vararg args: String): RecordedRun =
        runCapturing(workdir, args.toList(), env = mapOf("KATACHI_WORKDIR" to workdir.toString(), "KATACHI_DOCS" to base))
}
