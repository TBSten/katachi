package me.tbsten.katachi.test.dokka

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dokka.KatachiDokkaPlugin
import me.tbsten.katachi.dokka.featured.SummarySegment
import me.tbsten.katachi.dokka.fragment.FRAGMENT_FILE
import me.tbsten.katachi.dokka.fragment.FRAGMENT_SCHEMA_VERSION
import me.tbsten.katachi.dokka.fragment.FragmentEntry
import me.tbsten.katachi.dokka.fragment.ModuleFragment
import me.tbsten.katachi.dokka.fragment.ModuleFragmentRegistry
import me.tbsten.katachi.dokka.fragment.ModuleFragmentStrategy
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.DokkaModuleDescriptionImpl
import org.jetbrains.dokka.base.templating.toJsonString
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import testApi.testRunner.dokkaConfiguration
import java.io.File

class ModuleFragmentStrategySpec : FreeSpec({
    "断片のファイル" - {
        "読み取って registry に積み、true を返して出力へのコピーを止める" {
            withStrategy { context, strategy, directory ->
                val input = File(directory, FRAGMENT_FILE).apply { writeText(toJsonString(fragment(FRAGMENT_SCHEMA_VERSION))) }
                val output = File(directory, "out/$FRAGMENT_FILE")

                strategy.process(input, output, module("katachi", "katachi")) shouldBe true

                output.exists() shouldBe false
                context.registry().fragmentsOf(listOf("katachi")).single().featured.map { it.path } shouldContainExactly
                    listOf("katachi/sample/-architecture/index.html")
            }
        }

        "モジュールの置き場所が入れ子でも、区切りを / にして前に付ける" {
            withStrategy { context, strategy, directory ->
                val input = File(directory, FRAGMENT_FILE).apply { writeText(toJsonString(fragment(FRAGMENT_SCHEMA_VERSION))) }

                strategy.process(input, input, module("dokka", "tool${File.separator}dokka"))

                val fragment = context.registry().fragmentsOf(listOf("dokka")).single()
                fragment.featured.single().path shouldBe "tool/dokka/sample/-architecture/index.html"
                fragment.llmsFiles shouldContainExactly listOf("tool/dokka/llms.txt", "tool/dokka/llms-full.txt")
                fragment.modulePath shouldBe "tool/dokka/index.html"
            }
        }

        "schemaVersion が違う断片は積まないが、コピーもしない" {
            withStrategy { context, strategy, directory ->
                val input = File(directory, FRAGMENT_FILE).apply { writeText(toJsonString(fragment(FRAGMENT_SCHEMA_VERSION + 1))) }

                strategy.process(input, input, module("katachi", "katachi")) shouldBe true

                context.registry().fragmentsOf(listOf("katachi")).shouldBeEmpty()
            }
        }

        "壊れた JSON でも生成を止めない" {
            withStrategy { context, strategy, directory ->
                val input = File(directory, FRAGMENT_FILE).apply { writeText("{ not json") }

                strategy.process(input, input, module("katachi", "katachi")) shouldBe true

                context.registry().fragmentsOf(listOf("katachi")).shouldBeEmpty()
            }
        }
    }

    "ほかのファイルには false を返し、後ろの strategy に任せる" {
        withStrategy { _, strategy, directory ->
            val input = File(directory, "index.html").apply { writeText("<html></html>") }
            strategy.process(input, input, module("katachi", "katachi")) shouldBe false
        }
    }

    "モジュールの外から呼ばれたとき (束ねたページの後処理) は何もしない" {
        withStrategy { _, strategy, directory ->
            val input = File(directory, FRAGMENT_FILE).apply { writeText(toJsonString(fragment(FRAGMENT_SCHEMA_VERSION))) }
            strategy.process(input, input, null) shouldBe false
        }
    }
})

private fun withStrategy(block: (DokkaContext, ModuleFragmentStrategy, File) -> Unit) {
    val directory = temporaryDirectory()
    try {
        val configuration = dokkaConfiguration { outputDir = File(directory, "out") }
        val logger = DokkaConsoleLogger(LoggingLevel.WARN)
        val context = DokkaGenerator(configuration, logger).initializePlugins(configuration, logger)
        block(context, ModuleFragmentStrategy(context), directory)
    } finally {
        directory.deleteRecursively()
    }
}

private fun DokkaContext.registry(): ModuleFragmentRegistry =
    plugin<KatachiDokkaPlugin>().querySingle { moduleFragmentRegistry }

private fun module(name: String, relativePath: String) = DokkaModuleDescriptionImpl(
    name = name,
    relativePathToOutputDirectory = File(relativePath),
    includes = emptySet(),
    sourceOutputDirectory = File(name),
)

private fun fragment(schemaVersion: Int) = ModuleFragment(
    schemaVersion = schemaVersion,
    moduleName = "written-name",
    moduleSummary = null,
    featured = listOf(
        FragmentEntry(
            name = "Architecture",
            kind = "class",
            summary = listOf(SummarySegment("The architecture.", code = false)),
            path = "sample/-architecture/index.html",
        ),
    ),
    llmsFiles = listOf("llms.txt", "llms-full.txt"),
    modulePath = "index.html",
    pageMarkdown = true,
)
