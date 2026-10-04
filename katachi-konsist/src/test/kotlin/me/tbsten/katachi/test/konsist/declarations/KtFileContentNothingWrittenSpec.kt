package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.KatachiKonsistNoKotlinFilesException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentNothingWrittenException
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * A `ktFileContent { }` that cannot say anything about its files is refused rather than answered
 * with "nothing wrong": a block that writes no line for any file, and a role whose covered files
 * hold no `.kt` Konsist can read (decided D6, D21).
 */
class KtFileContentNothingWrittenSpec : FreeSpec({
    "覆うファイルに .kt が無ければ KatachiKonsistNoKotlinFilesException" {
        val violations = ktFileContentRun(
            "docs/README.md" to "# docs\n",
            paths = { "docs" / "*.md".file() },
        ) {
            ktFileContent { `class`(fileName) }
        }

        violations.filterIsInstance<UncheckedFileConstraint>().single().cause
            .shouldBeInstanceOf<KatachiKonsistNoKotlinFilesException>().role shouldBe "Screen"
    }

    "ファイルによっては何も書かなくても、どれかのファイルで書けば検査する" {
        val violations = ktFileContentRun(
            "src/Home.kt" to "package com.example\n\nclass Home\n",
            "src/Other.kt" to "package com.example\n\nobject Other\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent { if (fileName == "Home") `class`(fileName) }
        }

        violations.shouldBeEmpty()
    }

    "どのファイルでも書かなければ NothingWritten" {
        val violations = ktFileContentRun(
            "src/Home.kt" to "package com.example\n\nclass Home\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent { if (fileName == "Nothing") `class`(fileName) }
        }

        violations.filterIsInstance<UncheckedFileConstraint>().single().cause
            .shouldBeInstanceOf<KatachiKtFileContentNothingWrittenException>()
    }
})
