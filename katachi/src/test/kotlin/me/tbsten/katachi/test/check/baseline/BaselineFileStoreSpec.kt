package me.tbsten.katachi.test.check.baseline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.KatachiBaselineWriteException
import me.tbsten.katachi.check.internal.BaselineStore
import me.tbsten.katachi.check.internal.FileBaselineStore
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.dsl.baselineFile
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf
import java.io.IOException
import java.nio.file.Files

class BaselineFileStoreSpec : FreeSpec({
    "ディスク上の台帳" - {
        "無いファイルは null として読む" {
            val directory = Files.createTempDirectory("katachi-baseline").toFile()
            try {
                FileBaselineStore.read(directory.resolve("katachi-baseline.json").path).shouldBeNull()
            } finally {
                directory.deleteRecursively()
            }
        }

        "書くとディレクトリごと作られ、UTF-8 のまま読み戻せる" {
            val directory = Files.createTempDirectory("katachi-baseline").toFile()
            try {
                val file = directory.resolve("config/katachi/baseline.json").path
                FileBaselineStore.write(file, "{\"path\": \"日本語.md\"}\n")

                FileBaselineStore.read(file) shouldBe "{\"path\": \"日本語.md\"}\n"
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    "上書きは中身を丸ごと入れ替え、隣に一時ファイルを残さない" {
        val directory = Files.createTempDirectory("katachi-baseline").toFile()
        try {
            val file = directory.resolve("katachi-baseline.json").path
            FileBaselineStore.write(file, "old and longer\n")
            FileBaselineStore.write(file, "new\n")

            FileBaselineStore.read(file) shouldBe "new\n"
            directory.list()!!.toList() shouldBe listOf("katachi-baseline.json")
        } finally {
            directory.deleteRecursively()
        }
    }

    "書けなかったときは、そこにあったものを壊さず、一時ファイルも残さない" {
        val directory = Files.createTempDirectory("katachi-baseline").toFile()
        try {
            // A directory where the file should be: the move over it fails.
            val file = directory.resolve("katachi-baseline.json")
            file.resolve("occupied").mkdirs()

            shouldThrow<IOException> { FileBaselineStore.write(file.path, "new\n") }

            file.isDirectory shouldBe true
            directory.list()!!.toList() shouldBe listOf("katachi-baseline.json")
        } finally {
            directory.deleteRecursively()
        }
    }

    "書き込みの失敗は KatachiBaselineWriteException になり、ファイルの場所を示す" {
        val failing = object : BaselineStore {
            override fun read(file: String): String? = null
            override fun write(file: String, text: String): Unit = throw IOException("disk full")
        }
        val definition = architectureOf {
            baseline = baselineFile()
            "Readme" { layout { "README.md".file() } }
        }

        val failure = shouldThrow<KatachiBaselineWriteException> {
            definition.assertWith(repositoryOf { "notes.md"() }, emptyList(), 10, environmentOf(failing, update = true))
        }

        failure.file shouldBe BASELINE_URI
        failure.message shouldContain "disk full"
    }
})
