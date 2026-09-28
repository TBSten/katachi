package me.tbsten.katachi.test.dokka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dokka.internal.fragment.FRAGMENT_FILE
import me.tbsten.katachi.dokka.internal.fragment.FRAGMENT_SCHEMA_VERSION
import me.tbsten.katachi.dokka.internal.fragment.ModuleFragment
import org.jetbrains.dokka.base.templating.parseJson

class ModuleFragmentSpec : FreeSpec({
    "テンプレートの置き換えを遅らせる run (マルチモジュールのモジュール段)" - {
        val run by lazy {
            DokkaRunner.run(
                FeaturedSources.ALL_KINDS,
                DokkaRunner.configuration(moduleName = "sample-module", delayTemplateSubstitution = true),
            )
        }
        val fragment by lazy { parseJson<ModuleFragment>(run.files[FRAGMENT_FILE].shouldNotBeNull()) }

        "モジュールの出力の直下に断片の JSON を出す" {
            withClue(run.files.keys.sorted()) {
                run.files.keys.filter { it.endsWith(FRAGMENT_FILE) } shouldContainExactly listOf(FRAGMENT_FILE)
            }
        }

        "断片にはモジュール名と、並び順どおりの featured が入る" {
            fragment.schemaVersion shouldBe FRAGMENT_SCHEMA_VERSION
            fragment.moduleName shouldBe "sample-module"
            fragment.featured.map { it.name } shouldContainExactly FeaturedSources.ALL_KINDS_NAMES
        }

        "path は同じ run が書いたページを、モジュールの出力ルートからの相対で指す" {
            withClue(run.files.keys.sorted()) {
                fragment.featured.forEach { entry -> entry.path shouldBeIn run.files.keys }
            }
        }

        "断片には、同じ run が出力の直下に書いた llms のファイルが入る" {
            fragment.llmsFiles shouldContainExactly listOf("llms.txt", "llms-full.txt")
            fragment.llmsFiles.forEach { path -> path shouldBeIn run.files.keys }
        }

        "概要は地の文とコードの区切りを保ったまま運ばれる" {
            val assert = fragment.featured.first { it.name == "Architecture.assert" }
            assert.summary.map { it.code } shouldContainExactly listOf(false, true, false)
            assert.summary.joinToString("") { it.text } shouldBe
                "Runs every check and throws AssertionError on violations."
        }
    }

    "置き換えを遅らせない run (単一モジュール) は断片を出さない" {
        val run = DokkaRunner.run(FeaturedSources.ALL_KINDS)
        run.files.keys.filter { it.endsWith(FRAGMENT_FILE) } shouldContainExactly emptyList()
    }
})
