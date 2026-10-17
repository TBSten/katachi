package me.tbsten.katachi.intellij.ide.authoring.gutter

import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.presentation.authoring.AnchorKind
import org.jetbrains.kotlin.psi.KtFile

/**
 * The shapes of ide-authoring design 4.1 read from the PSI alone, one source each. The unresolved names are on purpose: the shapes
 * are matched on the spelling, so no library is needed.
 */
internal class DefinitionAnchorShapesTest : AnalysisTestBase() {
    private fun anchorsOf(text: String): List<Triple<AnchorKind, String?, Int>> {
        val file = myFixture.configureByText("Shapes.kt", text) as KtFile
        return runReadActionBlocking { DefinitionAnchors.of(file) }.map { Triple(it.anchor.kind, it.anchor.name, it.anchor.line) }
    }

    fun `test file entry に optional や metadata が挟まっても template は その entry の名前に結ばれる`() {
        val anchors = anchorsOf(
            """
            fun f() = "X".role {
                "X".ktFile().optional().template("id") { }
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(Triple(AnchorKind.Entry, "X.kt", 2), Triple(AnchorKind.Template, "X.kt", 2)),
            anchors.filter { it.first != AnchorKind.Role },
        )
    }

    fun `test file の entry は 補間をワイルドカードと読み 拡張子を足さない`() {
        val anchors = anchorsOf(
            """
            fun f() = "X".role {
                "${'$'}name.kt".file()
                    .template { }
            }
            """.trimIndent(),
        )

        assertEquals(listOf(Triple(AnchorKind.Entry, "*.kt", 2), Triple(AnchorKind.Template, "*.kt", 3)), anchors.filter { it.first != AnchorKind.Role })
    }

    fun `test 補間のある役割の名前は 分からないものとして anchor になる`() {
        val anchors = anchorsOf(
            """
            fun f() = "${'$'}{prefix}Service".role { }
            """.trimIndent(),
        )

        assertEquals(listOf(Triple(AnchorKind.Role, null, 1)), anchors)
    }

    fun `test 補間と ktsFile と 階層の入った名前は 最後の区切りだけを名前にする`() {
        val anchors = anchorsOf(
            """
            fun f() = "X".role {
                dir / "sub/${'$'}{capture("n")}Service".ktsFile()
                    .template { }
            }
            """.trimIndent(),
        )

        assertEquals(listOf(Triple(AnchorKind.Entry, "*Service.kts", 2), Triple(AnchorKind.Template, "*Service.kts", 3)), anchors.filter { it.first != AnchorKind.Role })
    }

    fun `test file entry ではない receiver の template は anchor にならない`() {
        val anchors = anchorsOf(
            """
            fun f() { "X".other().template { } ; "X".role }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), anchors)
    }
}
