package me.tbsten.katachi.intellij

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Keeps the Analysis API channel alive until a feature needs it: K2 analysis runs inside a
 * BasePlatformTestCase and resolves a type.
 *
 * TODO: replace with a test of a real feature once one reads Kotlin source.
 */
@OptIn(KaAllowAnalysisOnEdt::class)
internal class AnalysisApiSmokeTest : AnalysisTestBase() {

    fun `test Analysis API で関数の戻り値の型を解決できる`() {
        val file = myFixture.configureByText("Sample.kt", SAMPLE_SRC) as? KtFile
            ?: error("Sample.kt was not parsed as a Kotlin file")
        val function = file.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "repositoryName" }

        val returnType = runReadActionBlocking {
            allowAnalysisOnEdt {
                analyze(function) {
                    (function.symbol.returnType as? KaClassType)?.classId?.asFqNameString()
                }
            }
        }

        assertEquals("kotlin.String", returnType)
    }

    companion object {
        private val SAMPLE_SRC = """
            package com.example.sample

            fun repositoryName(name: String) = name + "Repository"
        """.trimIndent()
    }
}
