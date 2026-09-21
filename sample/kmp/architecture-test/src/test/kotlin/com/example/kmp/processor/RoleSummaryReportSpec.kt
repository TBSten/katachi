package com.example.kmp.processor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.process

/**
 * [RoleSummaryReport] run twice with two different `write` lambdas: once collecting, once
 * comparing. Nothing in [RoleSummaryReport] branches on which mode is running -- the mode is
 * entirely the lambda's business, which is the shape
 * `.local/features-by-version/v0.1/processor-api.md` describes for `ArchitectureProcessor<Unit>`
 * ("書くモードと確かめるモードは、注入するラムダが違うだけ").
 *
 * This file's own `@OptIn(ExperimentalKatachiApi::class)` is needed for calling
 * [me.tbsten.katachi.processor.process] below, which is itself `@ExperimentalKatachiApi`. The
 * more interesting proof that the wall also holds *through the alias* sits in
 * [RoleSummaryReport] itself: `: ArchitectureProcessorUnit` there does not compile without its
 * own `@OptIn`, even though that line never spells out [me.tbsten.katachi.processor.ArchitectureProcessor].
 * Both files opting in independently is the point -- implementing the typealias and calling the
 * processor API are two separate places the experimental marker has to be crossed, not one.
 */
@OptIn(ExperimentalKatachiApi::class)
class RoleSummaryReportSpec : FreeSpec({
    "書き込みモード: 集めるラムダを渡すと役割ごとに1件ずつ MD が集まる" {
        val written = mutableMapOf<String, String>()

        twoRoleArchitecture.process(RoleSummaryReport(write = { path, content -> written[path] = content }))

        written shouldBe mapOf(
            "First.md" to "# First\n",
            "Second.md" to "# Second\n",
        )
    }

    "確認モード: 役割を1つ外すと比べるラムダが消えたパスを検出する（--check 相当）" {
        // twoRoleArchitecture が書いたはずの内容を、先に集めるラムダで手に入れておく。
        val expected = mutableMapOf<String, String>()
        twoRoleArchitecture.process(RoleSummaryReport(write = { path, content -> expected[path] = content }))

        // Second を外した定義に対して、比べるラムダで同じ processor を回す。Second を
        // まだ知らない実装がこのラムダに来た場合は seen に "Second.md" が残ってしまい、
        // 下の assertion が失敗する -- 空振りしない作りになっている。
        val seen = mutableSetOf<String>()
        val mismatched = mutableSetOf<String>()
        oneRoleArchitecture.process(
            RoleSummaryReport(
                write = { path, content ->
                    seen += path
                    if (expected[path] != content) mismatched += path
                },
            ),
        )

        (expected.keys - seen) shouldContainExactly setOf("Second.md")
        mismatched.shouldBeEmpty()
    }
})

/** Two roles at the root, standing in for a report generated before a role was removed. */
private val twoRoleArchitecture: Architecture = architecture {
    "First" { }
    "Second" { }
}

/** [twoRoleArchitecture] with `Second` removed, standing in for the definition after it moved
 * on without regenerating the report. */
private val oneRoleArchitecture: Architecture = architecture {
    "First" { }
}
