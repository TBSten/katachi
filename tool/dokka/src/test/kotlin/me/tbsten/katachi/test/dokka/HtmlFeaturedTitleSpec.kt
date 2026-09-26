package me.tbsten.katachi.test.dokka

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * [FeaturedSources.ALL_KINDS] は llms/Markdown 用の見出し（`featuredTitle` そのまま）と、HTML 用の見出し
 * （星付き）が同じ run のどのファイルにどちらの形で出るかを確かめる。個々のページの見出し・サイドバーの検証は
 * [FeaturedSidebarSpec] と [ModulePageFeaturedSpec] が担うので、ここでは HTML と機械向けファイルの対比だけを見る。
 */
class HtmlFeaturedTitleSpec : FreeSpec({
    val run by lazy { DokkaRunner.run(FeaturedSources.ALL_KINDS, DokkaRunner.configuration(moduleName = "sample-module")) }

    "HTML の見出しには星が付く" - {
        "サイドバー（navigation.html）" {
            // ノードの名前は単語ごとに別の span に分かれて描かれるが、aria-label には分かれず入る。
            run.files["navigation.html"].shouldNotBeNull() shouldContain """aria-label="⭐️ Featured""""
        }

        "モジュールページ（index.html）の節見出し" {
            run.files["index.html"].shouldNotBeNull() shouldContain ">⭐️ Featured<"
        }
    }

    "llms ファイルとページごとの Markdown の見出しには星を付けない" - {
        "llms.txt" {
            val llmsTxt = run.files["llms.txt"].shouldNotBeNull()
            llmsTxt shouldContain "## Featured"
            llmsTxt shouldNotContain "⭐️"
        }

        "llms-full.txt" {
            val llmsFullTxt = run.files["llms-full.txt"].shouldNotBeNull()
            llmsFullTxt shouldContain "## Featured"
            llmsFullTxt shouldNotContain "⭐️"
        }

        "ページごとの index.html.md" {
            val pageMarkdown = run.files["index.html.md"].shouldNotBeNull()
            pageMarkdown shouldContain "## Featured"
            pageMarkdown shouldNotContain "⭐️"
        }
    }
})
