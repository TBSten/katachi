package me.tbsten.katachi.test.dokka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup

class PackageNavigationSidebarSpec : FreeSpec({
    fun sidebar(mode: String?): Pair<DokkaRun, SidebarNode> {
        val json = mode?.let { """{ "packageNavigation": "$it" }""" }
        val run = DokkaRunner.run(PackageNavigationSources.APP, DokkaRunner.configuration(moduleName = "app", pluginJson = json))
        return run to sidebarOf(run.files["navigation.html"].shouldNotBeNull()).single()
    }

    "既定（hierarchical-module-link）の単一モジュール" - {
        val result by lazy { sidebar(mode = null) }
        val module by lazy { result.second }

        "Featured が先頭で、その後ろに package が名前の階層で並ぶ" {
            module.children.map { it.name } shouldContainExactly listOf("⭐️ Featured", "com.example")
            module.children[1].outline() shouldContainExactly listOf(
                "com.example",
                "  data",
                "    local",
                "      LocalSource",
                "    remote",
                "      RemoteSource",
                "    defaultRepository()",
                "    Repository",
                "  domain.model",
                "    User",
                "  feature.foo.bar",
                "    BarScreen",
            )
        }

        "実在しない package のノードはモジュールのページを指し、畳んだノードは実在の package を指す" {
            val example = module.children.named("com.example")
            example.href shouldBe "index.html"
            example.children.named("domain.model").href shouldBe "app/com.example.domain.model/index.html"
            example.children.named("feature.foo.bar").href shouldBe "app/com.example.feature.foo.bar/index.html"
        }

        "どのノードのリンクも、同じ run が書いたページを指す" {
            val (run, _) = result
            val hrefs = listOf(module).flatten().map { it.href.substringBefore('#') }
            withClue("files: ${run.files.keys.sorted()}") {
                hrefs.filterNot { it in run.files } shouldContainExactly emptyList()
            }
        }

        "ラベルだけのノードは無い" {
            listOf(module).flatten().filter { it.labelOnly } shouldContainExactly emptyList()
        }
    }

    "hierarchical-no-link の単一モジュール" - {
        val result by lazy { sidebar(mode = "hierarchical-no-link") }
        val module by lazy { result.second }

        "並びは既定と同じで、実在しない package のノードだけがリンクの無いラベルになる" {
            module.children.map { it.name } shouldContainExactly listOf("⭐️ Featured", "com.example")
            val labels = listOf(module).flatten().filter { it.labelOnly }
            labels.map { it.name } shouldContainExactly listOf("com.example")
            labels.single().href shouldBe ""
        }

        "ラベル以外のリンクは、同じ run が書いたページを指す" {
            val (run, _) = result
            val hrefs = listOf(module).flatten().filterNot { it.labelOnly }.map { it.href.substringBefore('#') }
            hrefs.filterNot { it in run.files } shouldContainExactly emptyList()
        }

        "ラベルのクリックは、自分のノードを開閉する" {
            val html = result.first.files.getValue("navigation.html")
            val label = Jsoup.parseBodyFragment(html).selectFirst("span.toc--label-only").shouldNotBeNull()
            val part = label.closest(".toc--part").shouldNotBeNull()
            part.id() shouldBe "app-nav-submenu-1"
            label.attr("onclick") shouldBe "window.handleTocButtonClick(event, '${part.id()}')"
        }
    }

    "flat では Dokka の元のパッケージの一覧のまま、Featured が先頭" {
        val (_, module) = sidebar(mode = "flat")
        module.children.map { it.name } shouldContainExactly listOf(
            "⭐️ Featured",
            "com.example.data",
            "com.example.data.local",
            "com.example.data.remote",
            "com.example.domain.model",
            "com.example.feature.foo.bar",
        )
    }
})

/** The names of [this] and the nodes below it, indented two spaces per level. */
internal fun SidebarNode.outline(indent: String = ""): List<String> =
    listOf(indent + name) + children.flatMap { it.outline("$indent  ") }
