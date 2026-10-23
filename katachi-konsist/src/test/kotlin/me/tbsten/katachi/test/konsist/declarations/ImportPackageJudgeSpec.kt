package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.declarations.internal.directoryPackageOf

/** The judging of imports and of the package (impl-spec 8.7 and 8.8). */
class ImportPackageJudgeSpec : FreeSpec({
    "import" - {
        "* は区切り 1 つ、** はまたぐ" {
            judged("package com.example\n\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.flow.Flow\n\nclass Sample\n") {
                `import`("kotlinx.coroutines.*")
            } shouldBe listOf(
                "import kotlinx.coroutines.flow.Flow (line 4) | Not allowed: not in the allowed imports (kotlinx.coroutines.*)",
            )
        }

        "star import は a.b.* というパスで、alias は見ない" {
            judged("package com.example\n\nimport a.b.*\nimport c.D as E\n\nclass Sample\n") {
                `import`("a.b.*")
                `import`("c.D")
            }.shouldBeEmpty()
        }

        "必須の import が無ければ足りない" {
            judged("package com.example\n\nclass Sample\n") { `import`("com.example.App") } shouldBe
                listOf("import com.example.App | Missing: import com.example.App")
        }

        "禁止と except" {
            judged(
                "package com.example\n\nimport com.example.feature.home.A\nimport com.example.feature.settings.B\n\nclass Sample\n",
                captures = mapOf("feature" to "home"),
            ) {
                `import`("**")
                not.`import`("com.example.feature.**").except("com.example.feature.${captureValue("feature")}.**")
            } shouldBe listOf(
                "import com.example.feature.settings.B (line 4) | Not allowed: import com.example.feature.settings.B | " +
                    "Prohibited by: not import com.example.feature.** except com.example.feature.home.**",
            )
        }

        "禁止の行だけなら、許す import は無い" {
            judged("package com.example\n\nimport a.B\n\nclass Sample\n") { not.`import`("android.**") } shouldBe
                listOf("import a.B (line 3) | Not allowed: ktFileContent { } allows no import here")
        }
    }

    "package" - {
        "パターンに当たらなければ違反" {
            judged("package com.example.feature.homex\n\nclass Sample\n") { `package`("com.example.feature.home") } shouldBe
                listOf("package com.example.feature.homex (line 1) | Not allowed: package com.example.feature.homex")
        }

        "package の無いファイルは、package の行があれば違反" {
            judged("class Sample\n") { `package`("**") } shouldBe
                listOf("package (none) | Not allowed: the file declares no package")
        }

        "禁止に当たれば違反" {
            judged("package com.example.legacy.a\n\nclass Sample\n") {
                `package`("**")
                not.`package`("com.example.legacy.**")
            } shouldBe listOf(
                "package com.example.legacy.a (line 1) | Not allowed: package com.example.legacy.a | " +
                    "Prohibited by: not package com.example.legacy.**",
            )
        }

        "matchingDirectory は src/<名前>/kotlin/ の下のディレクトリと比べる" {
            val block: me.tbsten.katachi.konsist.declarations.KtFileContentScope.() -> Unit = { `package`(matchingDirectory) }
            judged("package com.example.home\n\nclass Sample\n", "feature/src/main/kotlin/com/example/home/Sample.kt", block = block)
                .shouldBeEmpty()
            judged("package com.example.home\n\nclass Sample\n", "app/src/main/java/com/example/home/Sample.kt", block = block)
                .shouldBeEmpty()
            judged("package com.example.homex\n\nclass Sample\n", "feature/src/main/kotlin/com/example/home/Sample.kt", block = block) shouldBe
                listOf("package com.example.homex (line 1) | Not allowed: package com.example.homex")
        }

        "matchingDirectory: ソースセットの無いパスは末尾が一致すればよい" {
            judged("package com.example\n\nclass Sample\n", "lib/com/example/Sample.kt") { `package`(matchingDirectory) }.shouldBeEmpty()
        }

        "matchingDirectory: kotlin/ 直下の package の無いファイルは通る" {
            judged("class Sample\n", "src/main/kotlin/Sample.kt") { `package`(matchingDirectory) }.shouldBeEmpty()
            judged("class Sample\n", "src/main/kotlin/com/Sample.kt") { `package`(matchingDirectory) } shouldBe
                listOf("package (none) | Not allowed: the file declares no package")
        }

        "ディレクトリの package の読み方" {
            directoryPackageOf("feature/home/src/main/kotlin/com/example/home/A.kt") shouldBe "com.example.home"
            directoryPackageOf("src/commonMain/kotlin/A.kt") shouldBe ""
            directoryPackageOf("src/main/kotlin/x/src/test/java/a/b/A.kt") shouldBe "a.b"
            directoryPackageOf("lib/com/example/A.kt").shouldBeNull()
        }
    }

    "並び: package、import、宣言の順で、足りないものは最後" {
        judged("package com.other\n\nimport a.B\n\nclass Extra\n") {
            `package`("com.example")
            `import`("kotlin.**")
            `import`("com.example.App")
            `class`(fileName)
        } shouldBe listOf(
            "package com.other (line 1) | Not allowed: package com.other",
            "import a.B (line 3) | Not allowed: not in the allowed imports (kotlin.**, com.example.App)",
            "Extra (line 5) | Not allowed: class Extra",
            "import com.example.App | Missing: import com.example.App",
            "Sample | Missing: class Sample",
        )
    }
})
