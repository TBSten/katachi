package me.tbsten.katachi.test.dokka

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dokka.navigation.KatachiDokkaPackageNavigationException
import java.io.File

class AllModulesPackageNavigationSpec : FreeSpec({
    val workDirectory = temporaryDirectory()
    afterSpec { workDirectory.deleteRecursively() }

    fun aggregate(name: String, mode: String?): Pair<File, List<SidebarNode>> {
        val json = mode?.let { """{ "packageNavigation": "$it" }""" }
        val output = generateMultiModule(
            File(workDirectory, name),
            mapOf("app" to PackageNavigationSources.APP, "core" to PackageNavigationSources.CORE),
            modulePluginJson = json,
            aggregatedPluginJson = json,
        )
        return output to sidebarOf(File(output, "navigation.html").readText())
    }

    "既定（hierarchical-module-link）で2つのモジュールを束ねた出力" - {
        val result by lazy { aggregate("module-link", mode = null) }
        val sidebar by lazy { result.second }

        "各モジュールの直下は Featured が先頭で、package は名前の階層" {
            sidebar.map { it.name } shouldContainExactly listOf("app", "core")
            sidebar.named("app").children.map { it.name } shouldContainExactly listOf("⭐️ Featured", "com.example")
            sidebar.named("core").outline() shouldContainExactly listOf(
                "core",
                "  ⭐️ Featured",
                "    Core",
                "  com.example.core",
                "    util",
                "      trimmed()",
                "    Core",
            )
        }

        "実在しない package のノードは自分のモジュールのページを指す" {
            sidebar.named("app").children.named("com.example").href shouldBe "app/index.html"
        }

        "どのリンクも束ねた出力にある実在のページを指す" {
            val (output, _) = result
            sidebar.flatten().forEach { node ->
                withClue(node.name) { File(output, node.href.substringBefore('#')).isFile shouldBe true }
            }
        }
    }

    "束ねる run だけに書いた packageNavigation の知らない値も弾く" {
        val thrown = shouldThrowAny {
            generateMultiModule(
                File(workDirectory, "unknown"),
                mapOf("app" to PackageNavigationSources.APP),
                aggregatedPluginJson = """{ "packageNavigation": "tree" }""",
            )
        }
        generateSequence(thrown) { it.cause }.filterIsInstance<KatachiDokkaPackageNavigationException>()
            .single().value shouldBe "tree"
    }

    "hierarchical-no-link でも、束ねたサイドバーはモジュールごとに階層で、仮想ノードはラベル" {
        val (output, sidebar) = aggregate("no-link", mode = "hierarchical-no-link")
        sidebar.named("app").children.map { it.name } shouldContainExactly listOf("⭐️ Featured", "com.example")
        sidebar.flatten().filter { it.labelOnly }.map { it.name } shouldContainExactly listOf("com.example")
        sidebar.flatten().filterNot { it.labelOnly }.forEach { node ->
            withClue(node.name) { File(output, node.href.substringBefore('#')).isFile shouldBe true }
        }
    }
})
