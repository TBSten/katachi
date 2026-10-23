package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.DeclarationListScope
import me.tbsten.katachi.konsist.declarations.TypeKind
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * The examples of design.md, run end to end over real files with `FileConstraintCheck()`: a file
 * written the way the example means passes, and one that breaks it fails with what the example
 * says. Each failure is written as `<path>: <declaration> (line <n>) | <label>: <text> | ...`.
 */

/** A user's own name for a kind, written as design.md 2.3 suggests. */
private val DeclarationListScope.classOrInterface: TypeKind get() = `class` or `interface`

class KtFileContentDesignExamplesSpec : FreeSpec({
    "要点の ViewModel" {
        val violations = ktFileContentRun(
            "feature/home/HomeViewModel.kt" to """
                package com.example.feature.home

                import androidx.lifecycle.ViewModel
                import com.example.domain.GetUser
                import dagger.hilt.android.lifecycle.HiltViewModel
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow

                @HiltViewModel
                class HomeViewModel(private val getUser: GetUser) : ViewModel() {
                    private val state = MutableStateFlow(HomeUiState())
                    val uiState: StateFlow<HomeUiState> = state
                    fun onRefresh() {}
                    private fun load() {}
                }
            """.trimIndent() + "\n",
            "feature/settings/SettingsViewModel.kt" to """
                package com.example.feature.settings

                import android.content.Context
                import androidx.lifecycle.ViewModel
                import dagger.hilt.android.lifecycle.HiltViewModel
                import kotlinx.coroutines.flow.StateFlow

                @HiltViewModel
                class SettingsViewModel(private val context: Context) : ViewModel() {
                    val uiState: StateFlow<SettingsUiState> = TODO()
                    var isLoading = false
                    fun onSave(): Int = 1
                }
            """.trimIndent() + "\n",
            paths = { "feature" / capture("feature") / "*ViewModel".ktFile() },
        ) {
            ktFileContent {
                `package`("com.example.feature.**")

                `import`("kotlin.**")
                `import`("kotlinx.coroutines.**")
                `import`("androidx.**")
                `import`("com.example.domain.**")
                `import`("dagger.hilt.**")
                `import`("javax.inject.**")

                annotated("HiltViewModel")
                    .public.`class`(fileName).superclass("ViewModel") {
                        public.`val`("uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
                        public.`fun`("on*")().returns("Unit")
                        private.anythingElse()
                    }
            }
        }

        violations.unsatisfiedLines() shouldBe listOf(
            "feature/settings/SettingsViewModel.kt: import android.content.Context (line 3) | " +
                "Not allowed: not in the allowed imports (kotlin.**, kotlinx.coroutines.**, androidx.**, com.example.domain.**, dagger.hilt.**, javax.inject.**)",
            "feature/settings/SettingsViewModel.kt: SettingsViewModel.isLoading (line 11) | " +
                "Not allowed: var isLoading in class SettingsViewModel",
            "feature/settings/SettingsViewModel.kt: SettingsViewModel.onSave (line 12) | " +
                "Not allowed: fun onSave(): Int in class SettingsViewModel",
        )
    }

    "UseCase: interface と Default の実装、メンバ" {
        val violations = ktFileContentRun(
            "core/domain/GetUserUseCase.kt" to """
                package com.example.domain

                interface GetUserUseCase {
                    operator fun invoke(): User
                }

                class DefaultGetUserUseCase(private val repository: UserRepository) : GetUserUseCase {
                    override fun invoke(): User = repository.get()
                }
            """.trimIndent() + "\n",
            "core/domain/SaveUserUseCase.kt" to """
                package com.example.domain

                interface SaveUserUseCase {
                    fun invoke()
                }

                class SaveUserUseCaseImpl : SaveUserUseCase {
                    override fun invoke() {}
                }
            """.trimIndent() + "\n",
            paths = { "core/domain" / "*UseCase".ktFile() },
        ) {
            ktFileContent {
                `interface`(fileName) {
                    operator.`fun`("invoke")
                }
                `class`("Default$fileName").superclass(fileName) {
                    override.`fun`("invoke")
                    private.anythingElse()
                }
            }
        }

        violations.unsatisfiedLines() shouldBe listOf(
            "core/domain/SaveUserUseCase.kt: SaveUserUseCase.invoke (line 4) | Not allowed: fun invoke() in interface SaveUserUseCase | " +
                "Expected: operator fun invoke(...) | Differs in: modifiers (missing: operator)",
            "core/domain/SaveUserUseCase.kt: SaveUserUseCaseImpl (line 7) | Not allowed: class SaveUserUseCaseImpl : SaveUserUseCase",
            "core/domain/SaveUserUseCase.kt: DefaultSaveUserUseCase | Missing: class DefaultSaveUserUseCase : SaveUserUseCase",
        )
    }

    "UiState: data class で、val だけを置く" {
        val violations = ktFileContentRun(
            "ui/HomeUiState.kt" to "package com.example\n\ndata class HomeUiState(val items: List<String> = emptyList())\n",
            "ui/SettingsUiState.kt" to "package com.example\n\ndata class SettingsUiState(var dark: Boolean) {\n    fun toggle() = copy(dark = !dark)\n}\n",
            paths = { "ui" / "*UiState".ktFile() },
        ) {
            ktFileContent {
                data.`class`(fileName) {
                    `val`("*")
                }
            }
        }

        violations.unsatisfiedLines() shouldBe listOf(
            "ui/SettingsUiState.kt: SettingsUiState.dark (line 3) | Not allowed: var dark: Boolean in data class SettingsUiState",
            "ui/SettingsUiState.kt: SettingsUiState.toggle (line 4) | Not allowed: fun toggle() in data class SettingsUiState",
        )
    }

    "import だけを縛る: 後ろの層を import しない（katachi 自身の規則）" {
        val violations = ktFileContentRun(
            "src/Check.kt" to "package me.tbsten.katachi.check\n\nimport me.tbsten.katachi.dsl.Role\n\nclass Check\nfun anything() {}\n",
            "src/Leak.kt" to "package me.tbsten.katachi.check\n\nimport me.tbsten.katachi.docs.Page\nimport me.tbsten.katachi.template.Template\n\nclass Leak\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent {
                `import`("**")
                not.`import`("me.tbsten.katachi.docs.**")
                not.`import`("me.tbsten.katachi.template.**")
            }
        }

        violations.unsatisfiedLines() shouldBe listOf(
            "src/Leak.kt: import me.tbsten.katachi.docs.Page (line 3) | " +
                "Not allowed: import me.tbsten.katachi.docs.Page | Prohibited by: not import me.tbsten.katachi.docs.**",
            "src/Leak.kt: import me.tbsten.katachi.template.Template (line 4) | " +
                "Not allowed: import me.tbsten.katachi.template.Template | Prohibited by: not import me.tbsten.katachi.template.**",
        )
    }

    "宣言だけを縛る: (class or interface) と利用者の拡張" {
        val violations = ktFileContentRun(
            "src/Home.kt" to "package com.example\n\nimport anything.At.All\n\ninterface Home\n",
            "src/Settings.kt" to "package com.example\n\nobject Settings\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent { classOrInterface(fileName) }
        }

        violations.unsatisfiedLines() shouldBe listOf(
            "src/Settings.kt: Settings (line 3) | Not allowed: object Settings | Expected: (class or interface) Settings | " +
                "Differs in: kind (is object, expected class or interface)",
        )
    }

    "修飾子・or・アノテーション" {
        val violations = ktFileContentRun(
            "src/HomeScreen.kt" to """
                package com.example

                @Composable
                fun HomeScreen() {}

                @Preview
                private fun HomeScreenPreview() {}

                @PreviewLightDark
                private fun HomeScreenDarkPreview() {}

                @Serializable
                data object HomeKey

                internal data object Saved

                fun interface ClickListener { fun onClick() }
            """.trimIndent() + "\n",
            "src/OtherScreen.kt" to """
                package com.example

                fun OtherScreen() {}

                @Preview
                fun OtherScreenPreview() {}
            """.trimIndent() + "\n",
            paths = { "src" / "*Screen".ktFile() },
        ) {
            ktFileContent {
                annotated("Composable").public.`fun`(fileName)
                (annotated("Preview") or annotated("PreviewLightDark")).private.`fun`("*Preview")
                annotated("Serializable")(`object` or `class`)("*Key")
                (internal or private).data.`object`("*")
                `fun`.`interface`("*Listener")
            }
        }

        violations.unsatisfiedLines() shouldBe listOf(
            "src/OtherScreen.kt: OtherScreen (line 3) | Not allowed: fun OtherScreen() | Expected: @Composable public fun OtherScreen(...) | " +
                "Differs in: annotations (missing: @Composable)",
            "src/OtherScreen.kt: OtherScreenPreview (line 5) | Not allowed: @Preview fun OtherScreenPreview()",
        )
    }

    "型・引数・戻り値" {
        val violations = ktFileContentRun(
            "src/Loader.kt" to """
                package com.example

                class Loader {
                    val state: kotlinx.coroutines.flow.StateFlow<LoaderState> = TODO()
                    fun load(id: String, page: Int): Unit {}
                    fun reset() {}
                    fun onEvent(event: Event) {}
                }
            """.trimIndent() + "\n",
            "src/BrokenLoader.kt" to """
                package com.example

                class BrokenLoader {
                    val state = TODO()
                    fun load(id: Int) {}
                    fun reset(): Boolean = true
                    fun onEvent() {}
                }
            """.trimIndent() + "\n",
            paths = { "src" / "*Loader".ktFile() },
        ) {
            ktFileContent {
                `class`(fileName) {
                    `val`("state", "StateFlow<*>")
                    `fun`("load")("String", "Int")
                    `fun`("reset")().returns("Unit")
                    `fun`("on*")(anyArgs())
                }
            }
        }

        violations.unsatisfiedLines() shouldBe listOf(
            "src/BrokenLoader.kt: BrokenLoader.state (line 4) | Not allowed: val state in class BrokenLoader | " +
                "Expected: val state: StateFlow<*> | Differs in: type (not written, expected StateFlow<*>)",
            "src/BrokenLoader.kt: BrokenLoader.load (line 5) | Not allowed: fun load(Int) in class BrokenLoader | " +
                "Expected: fun load(String, Int) | Differs in: parameters (are (Int), expected (String, Int))",
            "src/BrokenLoader.kt: BrokenLoader.reset (line 6) | Not allowed: fun reset(): Boolean in class BrokenLoader | " +
                "Expected: fun reset(): Unit | Differs in: return type (is Boolean, expected Unit)",
        )
    }

    "必須と任意: * を含まない import は必須" {
        val violations = ktFileContentRun(
            "src/Home.kt" to "package com.example\n\nimport com.example.App\nimport androidx.compose.ui.Modifier\n\nclass Home\n",
            "src/Settings.kt" to "package com.example\n\nclass Settings\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent {
                `import`("com.example.App")
                `import`("androidx.compose.**")
            }
        }

        violations.unsatisfiedLines() shouldBe listOf("src/Settings.kt: import com.example.App | Missing: import com.example.App")
    }

    "ほかは何でもよい: private.anythingElse() はファイル直下でも効く" {
        val violations = ktFileContentRun(
            "src/Home.kt" to "package com.example\n\nclass Home\nprivate const val TAG = \"x\"\nprivate fun helper() {}\nfun leaked() {}\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent {
                `class`(fileName)
                private.anythingElse()
            }
        }

        violations.unsatisfiedLines() shouldBe listOf("src/Home.kt: leaked (line 6) | Not allowed: fun leaked()")
    }

    "ファイルごとの分岐: Default の実装と interface" {
        val violations = ktFileContentRun(
            "nav/HomeScreenNavigator.kt" to "package com.example\n\ninterface HomeScreenNavigator : Navigator\n",
            "nav/DefaultHomeScreenNavigator.kt" to "package com.example\n\nclass DefaultHomeScreenNavigator : HomeScreenNavigator\n",
            "nav/DefaultSettingsScreenNavigator.kt" to "package com.example\n\nclass DefaultSettingsScreenNavigator : Navigator\n",
            paths = { "nav" / "*ScreenNavigator".ktFile() },
        ) {
            ktFileContent {
                if (fileName.startsWith("Default")) {
                    `class`(fileName).superclass(fileName.removePrefix("Default"))
                } else {
                    `interface`(fileName).superclass("Navigator")
                }
            }
        }

        violations.unsatisfiedLines() shouldBe listOf(
            "nav/DefaultSettingsScreenNavigator.kt: DefaultSettingsScreenNavigator (line 3) | " +
                "Not allowed: class DefaultSettingsScreenNavigator : Navigator | " +
                "Expected: class DefaultSettingsScreenNavigator : SettingsScreenNavigator | " +
                "Differs in: superclass (missing: SettingsScreenNavigator)",
        )
    }

    "沿うファイルだけなら違反は無い" {
        ktFileContentRun(
            "src/main/kotlin/com/example/home/HomeRepository.kt" to """
                package com.example.home

                import kotlinx.coroutines.flow.Flow

                interface HomeRepository {
                    fun items(): Flow<List<String>>
                }
            """.trimIndent() + "\n",
            paths = { "src/main/kotlin" / "**" / "*Repository".ktFile() },
        ) {
            ktFileContent {
                `package`(matchingDirectory)
                `import`("kotlinx.**")
                `interface`(fileName) { `fun`("*")().returns("Flow<*>") }
            }
        }.shouldBeEmpty()
    }
})
