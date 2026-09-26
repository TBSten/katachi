package me.tbsten.katachi.test.synthetic

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.validate

class SyntheticProjectSpec : FreeSpec({
    "生成は決定的" - {
        "同じ引数と種なら同じ木になる" {
            val first = generateProject(files = 500, roles = 5, modules = 10, seed = 7)
            val second = generateProject(files = 500, roles = 5, modules = 10, seed = 7)

            second.files shouldBe first.files
            second.roles shouldBe first.roles
            second.unknownDirectories shouldBe first.unknownDirectories
        }

        "種を変えると木が変わる" {
            val first = generateProject(files = 500, roles = 5, modules = 10, seed = 7)
            val second = generateProject(files = 500, roles = 5, modules = 10, seed = 8)

            second.files shouldNotBe first.files
        }
    }

    "指定どおりの規模になる" - {
        "ファイル数・ロール数・モジュール数が引数と一致する" {
            val project = generateProject(files = 1_000, roles = 20, modules = 100, seed = 1)

            project.files shouldHaveSize 1_000
            project.files.toSet() shouldHaveSize 1_000
            project.roles shouldHaveSize 20
            project.modules shouldHaveSize 100
            project.architecture().groups.single().roles shouldHaveSize 20
        }

        "ロールはモジュール展開と固定パスが交互に並ぶ" {
            val project = generateProject(files = 100, roles = 4, modules = 3)

            project.roles.map { it.placement } shouldBe listOf(
                SyntheticPlacement.Module,
                SyntheticPlacement.Fixed,
                SyntheticPlacement.Module,
                SyntheticPlacement.Fixed,
            )
        }

        "未知のディレクトリと ignore 対象が割合どおりに入る" {
            val project = generateProject(
                files = 1_000,
                roles = 5,
                modules = 10,
                unknownDirectoryRatio = 0.02,
                ignoredFileRatio = 0.05,
            )

            project.ignoredFiles shouldHaveSize 50
            project.files.count { "/stray" in "/$it" } shouldBe 20
            project.unknownDirectories shouldHaveSize 5
        }

        "固定部分すら入らないファイル数は拒否する" {
            shouldThrow<IllegalArgumentException> {
                generateProject(files = 10, roles = 5, modules = 10)
            }.message shouldContain "cannot hold"
        }
    }

    "validate の答えが想定どおり" - {
        "未知のディレクトリも ignore 対象もなければ違反は 0 件" {
            val project = generateProject(
                files = 1_000,
                roles = 5,
                modules = 10,
                unknownDirectoryRatio = 0.0,
                ignoredFileRatio = 0.0,
            )

            project.architecture().validate(project.inMemoryFileSystem()).labels().shouldBeEmpty()
        }

        "未知のディレクトリがそれぞれ1件の UnexpectedDirectory になる" {
            val project = generateProject(files = 1_000, roles = 5, modules = 10, seed = 3)

            val labels = project.architecture().validate(project.inMemoryFileSystem()).labels()

            project.expectedViolationLabels.size shouldBeGreaterThan 0
            labels.sorted() shouldBe project.expectedViolationLabels
        }

        "10k ファイル × ロール 20 × モジュール 100 でも想定どおり" {
            val project = generateProject(files = 10_000, roles = 20, modules = 100, seed = 5)

            project.architecture().validate(project.inMemoryFileSystem()).labels().sorted() shouldBe
                project.expectedViolationLabels
        }
    }

    "実ディレクトリに書き出した木はメモリ上の木と同じ答えになる" {
        val project = generateProject(files = 200, roles = 5, modules = 10, seed = 11)
        val directory = Files.createTempDirectory("katachi-synthetic").toFile()
        try {
            val fileSystem = project.writeTo(directory)

            project.architecture().validate(fileSystem).labels().sorted() shouldBe
                project.expectedViolationLabels
            val source = directory.resolve(project.roles.first().files.first()).readText()
            source shouldContain "package synthetic.role0"
        } finally {
            directory.deleteRecursively()
        }
    }
})

private fun List<Violation>.labels(): List<String> = map { "[${it.label}] ${it.path}" }
