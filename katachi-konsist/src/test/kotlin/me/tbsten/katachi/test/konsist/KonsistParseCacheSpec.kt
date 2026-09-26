@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.KonsistScope
import me.tbsten.katachi.konsist.internal.ParsedFileCache
import me.tbsten.katachi.konsist.konsist

/** `internal class Thing`. Same length as [PUBLIC_PADDED_KT], so a rewrite keeps the size. */
private val INTERNAL_SAME_SIZE_KT: String = """
    package com.example

    internal class Thing
""".trimIndent() + "\n"

/** `public class Thing`, padded to the byte length of [INTERNAL_SAME_SIZE_KT]. */
private val PUBLIC_PADDED_KT: String = """
    package com.example

    public   class Thing
""".trimIndent() + "\n"

/** One run of `FileConstraintCheck()` over [root], whose `src/` holds the files [declarations] name. */
private fun runOver(
    root: File,
    declarations: List<String> = listOf("*.kt"),
    block: KonsistScope.() -> Unit,
): List<Violation> {
    val projectArchitecture = architecture {
        files = wholeTree()
        "domain".group {
            "UseCase" {
                layout {
                    "gradlew".file()
                    "src" {
                        for (declaration in declarations) declaration.file()
                        "規約".konsist { block() }
                    }
                }
            }
        }
    }
    return projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
}

/** The parsed files one run's block was handed, in the order the scope lists them. */
private fun parsedFilesOf(root: File, declarations: List<String> = listOf("*.kt")): List<KoFileDeclaration> {
    val seen = mutableListOf<KoFileDeclaration>()
    runOver(root, declarations) {
        files.must {
            seen += it
            true
        }
    }
    return seen
}

private fun List<Violation>.rejectedPaths(): List<String> =
    filterIsInstance<UnsatisfiedFileConstraint>().map { it.path }

class KonsistParseCacheSpec : FreeSpec({
    beforeEach {
        ParsedFileCache.enabled = true
        ParsedFileCache.clear()
        // The fixtures are written a moment before they are parsed. Only the spec about the
        // window itself puts it back.
        ParsedFileCache.racyWindowNanos = 0
    }
    afterSpec {
        ParsedFileCache.enabled = true
        ParsedFileCache.racyWindowNanos = ParsedFileCache.DEFAULT_RACY_WINDOW_NANOS
        ParsedFileCache.clear()
    }

    "変わっていないファイルは2回目の run で解析し直さない" {
        fixtureProject("src/InternalThing.kt" to INTERNAL_THING_KT) { root ->
            val first = parsedFilesOf(root).single()
            val second = parsedFilesOf(root).single()

            second shouldBeSameInstanceAs first
        }
    }

    "中身が変わったファイルは解析し直し、新しい中身で判定する" {
        fixtureProject("src/Thing.kt" to INTERNAL_THING_KT) { root ->
            runOver(root) { classes().must { it.hasInternalModifier } }.shouldBeEmpty()

            File(root, "src/Thing.kt").writeText(PUBLIC_THING_KT)

            runOver(root) { classes().must { it.hasInternalModifier } }.rejectedPaths() shouldContainExactly
                listOf("src/Thing.kt")
        }
    }

    "サイズと mtime を元に戻して書き換えても古い解析を返さない" {
        fixtureProject("src/Thing.kt" to INTERNAL_SAME_SIZE_KT) { root ->
            val path = File(root, "src/Thing.kt").toPath()
            runOver(root) { classes().must { it.hasInternalModifier } }.shouldBeEmpty()
            val modified = Files.getLastModifiedTime(path)

            Files.writeString(path, PUBLIC_PADDED_KT)
            Files.setLastModifiedTime(path, modified)
            Files.size(path) shouldBe INTERNAL_SAME_SIZE_KT.length.toLong()

            runOver(root) { classes().must { it.hasInternalModifier } }.rejectedPaths() shouldContainExactly
                listOf("src/Thing.kt")
        }
    }

    "消して同じパスに同じサイズで作り直しても古い解析を返さない" {
        fixtureProject("src/Thing.kt" to INTERNAL_SAME_SIZE_KT) { root ->
            val path = File(root, "src/Thing.kt").toPath()
            runOver(root) { classes().must { it.hasInternalModifier } }.shouldBeEmpty()
            val modified = Files.getLastModifiedTime(path)

            Files.delete(path)
            Files.writeString(path, PUBLIC_PADDED_KT)
            Files.setLastModifiedTime(path, modified)

            runOver(root) { classes().must { it.hasInternalModifier } }.rejectedPaths() shouldContainExactly
                listOf("src/Thing.kt")
        }
    }

    "シンボリックリンクの先が書き換わったら古い解析を返さない" {
        val outside = Files.createTempDirectory("katachi-parse-cache-link")
        try {
            val target = outside.resolve("Thing.kt")
            Files.writeString(target, INTERNAL_THING_KT)
            fixtureProject { root ->
                val link = File(root, "src/Thing.kt").toPath()
                Files.createDirectories(link.parent)
                Files.createSymbolicLink(link, target)
                runOver(root) { classes().must { it.hasInternalModifier } }.shouldBeEmpty()

                // The link itself is untouched: only what it points at changes.
                Files.writeString(target, PUBLIC_THING_KT)

                runOver(root) { classes().must { it.hasInternalModifier } }.rejectedPaths() shouldContainExactly
                    listOf("src/Thing.kt")
            }
        } finally {
            outside.toFile().deleteRecursively()
        }
    }

    "キャッシュから引いても layout が覆わない兄弟ファイルは見えない" {
        fixtureProject(
            "src/PublicThing.kt" to PUBLIC_THING_KT,
            "src/InternalThing.kt" to INTERNAL_THING_KT,
        ) { root ->
            // Both files parsed and cached by the first run.
            parsedFilesOf(root).map { it.name } shouldContainExactly listOf("InternalThing", "PublicThing")

            parsedFilesOf(root, declarations = listOf("InternalThing.kt")).map { it.name } shouldContainExactly
                listOf("InternalThing")
        }
    }

    "書いたばかりのファイルはキャッシュしない" {
        ParsedFileCache.racyWindowNanos = ParsedFileCache.DEFAULT_RACY_WINDOW_NANOS
        fixtureProject("src/InternalThing.kt" to INTERNAL_THING_KT) { root ->
            val first = parsedFilesOf(root).single()
            val second = parsedFilesOf(root).single()

            second shouldNotBeSameInstanceAs first
        }
    }

    "無効にすると毎回解析する" {
        ParsedFileCache.enabled = false
        fixtureProject("src/InternalThing.kt" to INTERNAL_THING_KT) { root ->
            val first = parsedFilesOf(root).single()
            val second = parsedFilesOf(root).single()

            second shouldNotBeSameInstanceAs first
        }
    }

    "無効にするとそれまでの解析を捨て、有効に戻しても返さない" {
        fixtureProject("src/InternalThing.kt" to INTERNAL_THING_KT) { root ->
            val first = parsedFilesOf(root).single()
            ParsedFileCache.enabled = false
            ParsedFileCache.enabled = true
            val second = parsedFilesOf(root).single()

            second shouldNotBeSameInstanceAs first
        }
    }
})
