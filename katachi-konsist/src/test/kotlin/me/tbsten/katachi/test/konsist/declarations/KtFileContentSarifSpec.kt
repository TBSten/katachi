@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.report.SarifContext
import me.tbsten.katachi.test.report.withIsFile
import me.tbsten.katachi.report.toSarif

/**
 * What a `ktFileContent { }` that fails writes into `katachi.sarif`, whole: the SARIF the IDE plugin's annotations read
 * (`katachi-intellij-plugin/src/test/resources/contract/json/katachi-ktfilecontent-sarif.json` is a copy of the golden).
 *
 * The plugin tells a missing declaration from a rejected one by this output alone: a missing one has a `declaration`
 * property, no `region`, and a `Missing:` line in its message. That is why the golden is kept here, next to the code that writes it.
 *
 * The files exist as far as the context goes (`isFile = { true }`), so every result points at its own file, as in a real project.
 *
 * The rules are written on the lines the golden's `declaredAt` names: do not move them.
 * Rewrite the golden with `-Dkatachi.golden.update=true`.
 */
class KtFileContentSarifSpec : FreeSpec({
    "ktFileContent の 余計な宣言・足りない宣言・形違いの宣言を SARIF にした文書が golden と一致する" {
        val violations = ktFileContentRun(
            "core/domain/GetUserUseCase.kt" to
                "package com.example.domain\n\ninterface GetUserUseCase\n\nclass GetUserUseCaseImpl : GetUserUseCase\n",
            "core/domain/HomeViewModel.kt" to
                "package com.example.domain\n\nclass HomeViewModel {\n    private val uiState: Int = 0\n}\n",
            paths = { "core/domain" / "*".ktFile() },
        ) {
            ktFileContent { `package`("**") // on this line, so that the lines below stay where the golden names them
                required(`interface`, fileName)
                required(`class`, "Default$fileName").superclass(fileName)
            }
        }
        // A member that exists in another shape (the `Found:` line); the import line only allows, so it is never missing.
        val members = ktFileContentRun(
            "core/ui/CounterViewModel.kt" to "package com.example.ui\n\nclass CounterViewModel {\n    private val count = 1\n}\n",
            paths = { "core/ui" / "*ViewModel".ktFile() },
        ) {
            ktFileContent { `package`("**") // on this line, so that the lines below stay where the golden names them
                `import`("com.example.App")
                required(`class`, fileName) {
                    required(public, `val`, "count").type("Int")
                    allow(private, anythingElse)
                }
            }
        }
        val sarif = (violations + members).toSarif(SarifContext(projectRoot = FsPath.of("/w/repo/android"), repositoryPrefix = "android/", toolVersion = "0.0.0-test").withIsFile { true })
        checkGolden("ktfilecontent.sarif", sarif)
    }
})

private fun checkGolden(name: String, actual: String) {
    val directory = java.io.File("src/test/resources/sarif").takeIf { it.isDirectory } ?: java.io.File("katachi-konsist/src/test/resources/sarif")
    val file = directory.resolve(name)
    if (!file.isFile || System.getProperty("katachi.golden.update").equals("true", ignoreCase = true)) {
        file.writeText(actual, Charsets.UTF_8)
        return
    }
    file.readText(Charsets.UTF_8) shouldBe actual
}
