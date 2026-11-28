package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.KatachiFileConstraintReadException
import me.tbsten.katachi.dsl.internal.isRefusedFileConstraintName
import me.tbsten.katachi.test.check.architectureOf
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/**
 * What [FileConstraintSubject.textOf] and [FileConstraintSubject.lineOf] read from under the
 * project root, and the name rule the konsist module asks `:katachi` about.
 *
 * The subject is built directly over a directory on disk, because reading is the thing being pinned.
 */
private fun subjectOver(root: java.io.File, vararg files: String): FileConstraintSubject = FileConstraintSubject(
    role = architectureOf { "app".group { "Role".role { } } }.allRoles.single(),
    name = null,
    declaredAt = DeclarationSite("ProjectArchitecture.kt", 1),
    paths = emptyList(),
    projectRoot = root.invariantSeparatorsPath,
    files = files.toList(),
    projectFiles = files.toList(),
    shared = ConcurrentHashMap(),
)

class FileConstraintSubjectReadSpec : FreeSpec({
    "FileConstraintSubject の読み取り" - {
        val root = Files.createTempDirectory("katachi-subject-read").toFile().also { dir ->
            dir.resolve("app").mkdirs()
            dir.resolve("app/build.gradle.kts").writeText("plugins {\n}\nandroid {\n    namespace = \"a\"\n}\nnamespace = \"b\"\n")
        }
        val subject = subjectOver(root, "app/build.gradle.kts", "app/missing.kts")

        "textOf はプロジェクトルートからの相対パスのファイルの中身を返す" {
            subject.textOf("app/build.gradle.kts").lines().first() shouldBe "plugins {"
        }

        "lineOf は最初に一致した行を 1 始まりで返す" {
            subject.lineOf("app/build.gradle.kts", Regex("""namespace\s*=""")) shouldBe 4
        }

        "lineOf はどの行にも一致しなければ null" {
            subject.lineOf("app/build.gradle.kts", Regex("nothing")).shouldBeNull()
        }

        "読めないファイルは KatachiFileConstraintReadException になり、ファイル名と元の原因を持つ" {
            val failure = shouldThrow<KatachiFileConstraintReadException> { subject.textOf("app/missing.kts") }

            failure.file shouldBe "app/missing.kts"
            failure.projectRoot shouldBe root.invariantSeparatorsPath
            failure.cause?.javaClass shouldBe java.io.FileNotFoundException::class.java
            failure.message shouldContain "app/missing.kts"
        }
    }

    "constraint の名前の規則" - {
        "空白だけの名前と、改行を含む名前は断られる" {
            isRefusedFileConstraintName("") shouldBe true
            isRefusedFileConstraintName("   ") shouldBe true
            isRefusedFileConstraintName("a\nb") shouldBe true
            isRefusedFileConstraintName("a\rb") shouldBe true
        }

        "ふつうの名前は断られない" {
            isRefusedFileConstraintName("uses the catalog") shouldBe false
        }
    }
})
