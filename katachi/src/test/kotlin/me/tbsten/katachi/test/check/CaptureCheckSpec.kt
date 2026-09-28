package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.wildcard
import me.tbsten.katachi.dsl.gradle.wildcards
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

/** A tree with a feature module that holds a file of the role, a file of no role and a missing directory's sibling. */
private fun featureTree(): FakeFileSystem = repositoryOf {
    "feature" {
        "home" {
            "build.gradle.kts"()
            "src" { "HomeViewModel.kt"(); "Stray.txt"() }
        }
        "settings" {
            "build.gradle.kts"()
            "src" { "SettingsViewModel.kt"(); "OtherViewModel.kt"() }
            "extra" { "Note.kt"() }
        }
    }
    "docs" {
        "home" { "HomeViewModel.kt"(); "README.md"() }
        "deep" { "nested" { "X.kt"() } }
    }
}

/** The violations [block] finds in [featureTree], as report lines. */
private fun violationsOf(block: LayoutScope.() -> Unit): List<String> =
    layoutArchitecture(block = block).validate(featureTree()).labels().sorted()

/**
 * A named wildcard checks exactly as the `*` it names: the same tree gives the same violations,
 * line for line. [me.tbsten.katachi.test.dsl.LayoutCaptureSpec] compares the flattened entries;
 * this compares what the check actually reports over a fake file system.
 */
class CaptureCheckSpec : FreeSpec({
    "capture(...) で名前を付けても、検査の違反の列は * と同じ" - {
        "パスの capture" {
            val named = violationsOf { "docs" / capture("feature") / "*ViewModel".ktFile() }
            val plain = violationsOf { "docs" / "*" / "*ViewModel".ktFile() }

            named.shouldNotBeEmpty()
            named shouldBe plain
        }

        "ブロックとして書いたパスの capture" {
            val named = violationsOf { "docs" { capture("feature") { "*ViewModel".ktFile() } } }
            val plain = violationsOf { "docs" { "*" { "*ViewModel".ktFile() } } }

            named.shouldNotBeEmpty()
            named shouldBe plain
        }

        "モジュールの capture（wildcard(name) と wildcards[0] が同じファイル名を作る）" {
            val named = violationsOf {
                ":feature:${capture("feature")}".module {
                    "src" / "${wildcard("feature").replaceFirstChar(Char::uppercaseChar)}ViewModel".ktFile()
                }
            }
            val plain = violationsOf {
                ":feature:*".module {
                    "src" / "${wildcards[0].replaceFirstChar(Char::uppercaseChar)}ViewModel".ktFile()
                }
            }

            named.shouldNotBeEmpty()
            named shouldBe plain
        }
    }
})
