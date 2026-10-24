@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * What a file that breaks `ktFileContent { }` reads as in the report, whole: the seven shapes of
 * impl-spec 6.2 (design.md "4. 落ちたときの見え方"), with every line katachi writes.
 *
 * The lines of the rules are captured where they are written (`site = DeclarationSite.capture()`
 * on the same line), since `ktFileContent { }` takes them from the call stack.
 */
class KtFileContentReportSpec : FreeSpec({
    "(1) 余計な宣言と (5) 足りない宣言" {
        var block: DeclarationSite? = null
        var defaultLine: DeclarationSite? = null
        val violations = ktFileContentRun(
            "core/domain/GetUserUseCase.kt" to
                "package com.example.domain\n\ninterface GetUserUseCase\n\nclass GetUserUseCaseImpl : GetUserUseCase\n",
            paths = { "core/domain" / "*UseCase".ktFile() },
        ) {
            block = DeclarationSite.capture(); ktFileContent {
                required(`interface`, fileName)
                defaultLine = DeclarationSite.capture(); required(`class`, "Default$fileName").superclass(fileName)
            }
        }

        violations.report() shouldBe """
            Katachi check failed: 2 violations (FileConstraint: 2)

            [UnsatisfiedFileConstraint] core/domain/GetUserUseCase.kt
              Role: Screen / FileConstraint: "ktFileContent"
              Declaration: GetUserUseCaseImpl (line 5)
              Not allowed: class GetUserUseCaseImpl : GetUserUseCase
              Allowed here:
                interface GetUserUseCase
                class DefaultGetUserUseCase : GetUserUseCase  (missing)
              Declared at: $block

              How to fix:
                - Rename it to DefaultGetUserUseCase
                - Move it to a file of another role
                - Allow it in ktFileContent { } at $block:
                    allow(`class`, "GetUserUseCaseImpl")

            [UnsatisfiedFileConstraint] core/domain/GetUserUseCase.kt
              Role: Screen / FileConstraint: "ktFileContent"
              Declaration: DefaultGetUserUseCase
              Missing: class DefaultGetUserUseCase : GetUserUseCase
              Declared at: $defaultLine

              How to fix:
                - Declare it in this file
                - If some files do not need it, write allow(...) in place of required(...) at $defaultLine
        """.trimIndent()
    }

    "(2) 禁止の行に当たった宣言" {
        var prohibited: DeclarationSite? = null
        val violations = ktFileContentRun(
            "src/Foo.kt" to "package com.example\n\nclass Foo\n\nclass FooImpl\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent {
                allow(`class`, "*")
                prohibited = DeclarationSite.capture(); not.allow(`class`, "*Impl")
            }
        }

        violations.report() shouldBe """
            Katachi check failed: 1 violation (FileConstraint: 1)

            [UnsatisfiedFileConstraint] src/Foo.kt
              Role: Screen / FileConstraint: "ktFileContent"
              Declaration: FooImpl (line 5)
              Not allowed: class FooImpl
              Prohibited by: not class *Impl
              Declared at: $prohibited

              How to fix:
                - Remove it or rename it
                - If it is fine here, exclude it from the prohibited line at $prohibited:
                    .except("FooImpl")
        """.trimIndent()
    }

    "(3) 許されていない import と、禁止の行に当たった import" {
        var block: DeclarationSite? = null
        var prohibited: DeclarationSite? = null
        val violations = ktFileContentRun(
            "feature/home/HomeViewModel.kt" to """
                package com.example.feature.home

                import android.content.Context
                import com.example.domain.GetUser
                import com.example.feature.settings.SettingsRepository

                class HomeViewModel
            """.trimIndent() + "\n",
            paths = { "feature" / capture("feature") / "*ViewModel".ktFile() },
        ) {
            block = DeclarationSite.capture(); ktFileContent {
                `import`("kotlin.**")
                `import`("com.example.**")
                prohibited = DeclarationSite.capture(); not.`import`("com.example.feature.**")
                    .except("com.example.feature.${captureValue("feature")}.**")
            }
        }

        violations.report() shouldBe """
            Katachi check failed: 2 violations (FileConstraint: 2)

            [UnsatisfiedFileConstraint] feature/home/HomeViewModel.kt
              Role: Screen / FileConstraint: "ktFileContent"
              Declaration: import android.content.Context (line 3)
              Not allowed: not in the allowed imports (kotlin.**, com.example.**)
              Declared at: $block

              How to fix:
                - Remove the import and receive what it needs from outside
                - Allow it in ktFileContent { } at $block:
                    `import`("android.content.Context")

            [UnsatisfiedFileConstraint] feature/home/HomeViewModel.kt
              Role: Screen / FileConstraint: "ktFileContent"
              Declaration: import com.example.feature.settings.SettingsRepository (line 5)
              Not allowed: import com.example.feature.settings.SettingsRepository
              Prohibited by: not import com.example.feature.** except com.example.feature.home.**
              Declared at: $prohibited

              How to fix:
                - Remove the import and receive what it needs from outside
                - If this import is fine, exclude it from the prohibited line at $prohibited:
                    .except("com.example.feature.settings.SettingsRepository")
        """.trimIndent()
    }

    "(4) メンバ" {
        var classLine: DeclarationSite? = null
        val violations = ktFileContentRun(
            "src/HomeViewModel.kt" to """
                package com.example

                class HomeViewModel : ViewModel() {
                    val uiState: StateFlow<HomeUiState> = TODO()
                    var isLoading = false
                    fun onRefresh() {}
                    private fun load() {}
                }
            """.trimIndent() + "\n",
            paths = { "src" / "*ViewModel".ktFile() },
        ) {
            ktFileContent {
                classLine = DeclarationSite.capture(); required(public, `class`, fileName).superclass("ViewModel") {
                    required(public, `val`, "uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
                    allow(public, `fun`, "on*")().returns("Unit")
                    allow(private, anythingElse)
                }
            }
        }

        violations.report() shouldBe """
            Katachi check failed: 1 violation (FileConstraint: 1)

            [UnsatisfiedFileConstraint] src/HomeViewModel.kt
              Role: Screen / FileConstraint: "ktFileContent"
              Declaration: HomeViewModel.isLoading (line 5)
              Not allowed: var isLoading in class HomeViewModel
              Allowed members of class HomeViewModel:
                public val uiState: StateFlow<HomeUiState>
                public fun on*(): Unit
                private (anything)
              Declared at: $classLine

              How to fix:
                - Make it private: private (anything) is allowed here
                - Move it out of class HomeViewModel
                - Allow it in ktFileContent { } at $classLine:
                    allow(`var`, "isLoading")
        """.trimIndent()
    }

    "(5) 足りないメンバと Found の行。* の無い import の行は許すだけで足りないにならない" {
        var uiState: DeclarationSite? = null
        val violations = ktFileContentRun(
            "src/HomeViewModel.kt" to """
                package com.example

                class HomeViewModel {
                    private val uiState = 1
                }
            """.trimIndent() + "\n",
            paths = { "src" / "*ViewModel".ktFile() },
        ) {
            ktFileContent {
                `import`("com.example.App")
                required(`class`, fileName) {
                    uiState = DeclarationSite.capture(); required(public, `val`, "uiState", "StateFlow<*>")
                    allow(private, anythingElse)
                }
            }
        }

        violations.report() shouldBe """
            Katachi check failed: 1 violation (FileConstraint: 1)

            [UnsatisfiedFileConstraint] src/HomeViewModel.kt
              Role: Screen / FileConstraint: "ktFileContent"
              Declaration: HomeViewModel.uiState
              Missing: public val uiState: StateFlow<*> in class HomeViewModel
              Found: private val uiState (line 4)
              Declared at: $uiState

              How to fix:
                - Change the declaration at line 4 to: public val uiState: StateFlow<*>
                - Or change the line at $uiState if the declaration is right
                - If some files do not need it, write allow(...) in place of required(...) at $uiState
        """.trimIndent()
    }

    "(6) 名前は合うが形が違う宣言は 1 件" {
        var dataLine: DeclarationSite? = null
        val violations = ktFileContentRun(
            "src/HomeUiState.kt" to "package com.example\n\nclass HomeUiState(val items: List<String>)\n",
            paths = { "src" / "*UiState".ktFile() },
        ) {
            ktFileContent {
                dataLine = DeclarationSite.capture(); required(data, `class`, fileName) { allow(`val`, "*") }
            }
        }

        violations.report() shouldBe """
            Katachi check failed: 1 violation (FileConstraint: 1)

            [UnsatisfiedFileConstraint] src/HomeUiState.kt
              Role: Screen / FileConstraint: "ktFileContent"
              Declaration: HomeUiState (line 3)
              Not allowed: class HomeUiState
              Expected: data class HomeUiState
              Differs in: modifiers (missing: data)
              Declared at: $dataLine

              How to fix:
                - Change the declaration to: data class HomeUiState
                - Or change the line at $dataLine if the declaration is right
        """.trimIndent()
    }

    "(7) package" {
        var packageLine: DeclarationSite? = null
        val violations = ktFileContentRun(
            "feature/home/src/main/kotlin/com/example/feature/home/HomeViewModel.kt" to
                "package com.example.feature.homex\n\nclass HomeViewModel\n",
            "feature/home/src/main/kotlin/com/example/feature/home/NoPackageViewModel.kt" to "class NoPackageViewModel\n",
            paths = { "feature" / capture("feature") / "src/main/kotlin" / "**" / "*ViewModel".ktFile() },
        ) {
            ktFileContent {
                packageLine = DeclarationSite.capture(); `package`("com.example.feature.${captureValue("feature")}")
                `package`(matchingDirectory)
            }
        }

        violations.report() shouldBe """
            Katachi check failed: 2 violations (FileConstraint: 2)

            [UnsatisfiedFileConstraint] feature/home/src/main/kotlin/com/example/feature/home/HomeViewModel.kt
              Role: Screen / FileConstraint: "ktFileContent"
              Declaration: package com.example.feature.homex (line 1)
              Not allowed: package com.example.feature.homex
              Allowed here:
                package com.example.feature.home
                package com.example.feature.home  (the file's directory)
              Declared at: $packageLine

              How to fix:
                - Change the package to one allowed above
                - Or move the file to the directory its package names

            [UnsatisfiedFileConstraint] feature/home/src/main/kotlin/com/example/feature/home/NoPackageViewModel.kt
              Role: Screen / FileConstraint: "ktFileContent"
              Declaration: package (none)
              Not allowed: the file declares no package
              Allowed here:
                package com.example.feature.home
                package com.example.feature.home  (the file's directory)
              Declared at: $packageLine

              How to fix:
                - Declare a package allowed above
        """.trimIndent()
    }

    "katachi が書く文面は ASCII だけ" {
        val violations = ktFileContentRun(
            "src/Foo.kt" to "package com.example\n\nimport a.B\n\nclass Foo { var x = 1 }\nclass FooImpl\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent {
                `package`(matchingDirectory)
                `import`("kotlin.**")
                required(`class`, fileName) { }
                required(data, `class`, "FooImpl")
                not.allow(`object`, "*")
                required(`interface`, "Missing")
            }
        }

        violations.report().filter { it.code > 127 } shouldBe ""
    }
})
