package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.declarations
import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.withTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the anchors of a definition file are tied to the declarations of the model (ide-authoring design 4.2). */
class AnchorBindingTest {
    private val arch = module(":arch").id
    private val file = "ServiceRole.kt"

    private fun declarations(vararg roles: me.tbsten.katachi.intellij.model.RoleModel) = definitionModel(*roles).declarations(arch)

    private fun service(line: Int = 2, entryLine: Int = 3) =
        roleModel("domain.Service", layoutEntry("src/*Service.kt").declaredAt(file, entryLine)).declaredAt(file, line)

    @Test
    fun `同じ名前の役割の行は その役割に結ばれる`() {
        val bound = AnchorBinding.bind(listOf(Anchor(AnchorKind.Role, "Service", 2)), declarations(service()))

        assertEquals(listOf("domain.Service"), bound.single().declarations.map { it.role!!.qualifiedName })
    }

    @Test
    fun `名前の違う行には 行が同じでも何も結ばない`() {
        val bound = AnchorBinding.bind(listOf(Anchor(AnchorKind.Role, "Svc", 2)), declarations(service()))

        assertEquals(emptyList<Any>(), bound.single().declarations)
    }

    @Test
    fun `編集で行がずれても 名前が同じなら結ばれる`() {
        val bound = AnchorBinding.bind(listOf(Anchor(AnchorKind.Role, "Service", 42)), declarations(service()))

        assertEquals(1, bound.single().declarations.size)
    }

    @Test
    fun `同じ名前が複数あれば 宣言の行が最も近いものを選ぶ`() {
        val api = roleModel("api.Model", layoutEntry("api/*.kt")).declaredAt(file, 10)
        val domain = roleModel("domain.Model", layoutEntry("domain/*.kt")).declaredAt(file, 30)

        val near30 = AnchorBinding.bind(listOf(Anchor(AnchorKind.Role, "Model", 28)), declarations(api, domain))
        val near10 = AnchorBinding.bind(listOf(Anchor(AnchorKind.Role, "Model", 12)), declarations(api, domain))

        assertEquals(listOf("domain.Model"), near30.single().declarations.map { it.role!!.qualifiedName })
        assertEquals(listOf("api.Model"), near10.single().declarations.map { it.role!!.qualifiedName })
    }

    @Test
    fun `ワイルドカードの module key が展開した entry は 同じ行なので全部が1つの anchor に結ばれる`() {
        val role = roleModel(
            "feature.Screen",
            layoutEntry("feature/a/src/*Screen.kt", index = 0).declaredAt(file, 5),
            layoutEntry("feature/b/src/*Screen.kt", index = 1).declaredAt(file, 5),
            layoutEntry("feature/c/src/*Screen.kt", index = 2).declaredAt(file, 5),
        )

        val bound = AnchorBinding.bind(listOf(Anchor(AnchorKind.Entry, "*Screen.kt", 5)), declarations(role))

        assertEquals(3, bound.single().declarations.size)
    }

    @Test
    fun `名前の無い anchor は 同じ種類の宣言のうち最も近い行のものに結ばれる`() {
        val first = roleModel("a.One", layoutEntry("a/*.kt")).declaredAt(file, 3)
        val second = roleModel("a.Two", layoutEntry("b/*.kt")).declaredAt(file, 20)

        val bound = AnchorBinding.bind(listOf(Anchor(AnchorKind.Role, null, 19)), declarations(first, second))

        assertEquals(listOf("a.Two"), bound.single().declarations.map { it.role!!.qualifiedName })
    }

    @Test
    fun `entry は 最後のセグメントで結ばれ ディレクトリや anyFile の同じ末尾には結ばれない`() {
        val role = roleModel(
            "domain.Service",
            layoutEntry("src/*Service.kt", index = 0).declaredAt(file, 3),
            layoutEntry("src/*Service.kt", LayoutEntryKind.Directory, index = 1).declaredAt(file, 4),
            layoutEntry("src/*Service.kt", LayoutEntryKind.AnyFile, index = 2).declaredAt(file, 5),
        )

        val bound = AnchorBinding.bind(listOf(Anchor(AnchorKind.Entry, "*Service.kt", 4)), declarations(role))

        assertEquals(listOf(3), bound.single().declarations.map { it.site.line })
    }

    @Test
    fun `template の anchor は template を持つ entry の宣言に結ばれ 種類の違う宣言には結ばれない`() {
        val role = roleModel("domain.Service", layoutEntry("src/*Service.kt").declaredAt(file, 3).withTemplate("domain.Service", file, 4)).declaredAt(file, 2)

        val template = AnchorBinding.bind(listOf(Anchor(AnchorKind.Template, "*Service.kt", 4)), declarations(role)).single()
        val roleAnchor = AnchorBinding.bind(listOf(Anchor(AnchorKind.Role, "Service", 4)), declarations(role)).single()

        assertEquals(listOf(4), template.declarations.map { it.site.line })
        assertTrue(roleAnchor.declarations.all { it.role!!.name == "Service" && it.entry == null })
    }

    @Test
    fun `結果は anchor と同じ順序で 同じ数ある`() {
        val anchors = listOf(Anchor(AnchorKind.Role, "Service", 2), Anchor(AnchorKind.Entry, "nope", 3), Anchor(AnchorKind.Entry, "*Service.kt", 3))

        val bound = AnchorBinding.bind(anchors, declarations(service()))

        assertEquals(anchors, bound.map { it.anchor })
        assertEquals(listOf(1, 0, 1), bound.map { it.declarations.size })
    }
}
