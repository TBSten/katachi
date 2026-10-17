package me.tbsten.katachi.intellij.model

import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.withTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a definition says about where it declared things (ide-authoring design 4.2). */
class DefinitionDeclarationsTest {
    private val arch = module(":arch").id

    @Test
    fun `役割 entry template を宣言の場所つきで 種類ごとに1つずつ列挙する`() {
        val role = roleModel("domain.Service", layoutEntry("src/*Service.kt").declaredAt("ServiceRole.kt", 3).withTemplate("domain.Service.s", "ServiceRole.kt", 4))
            .declaredAt("ServiceRole.kt", 2)

        val found = definitionModel(role).declarations(arch).map { Triple(it.kind, it.site.fileName, it.site.line) }

        assertEquals(
            listOf(
                Triple(DeclarationKind.Role, "ServiceRole.kt", 2),
                Triple(DeclarationKind.Entry, "ServiceRole.kt", 3),
                Triple(DeclarationKind.Template, "ServiceRole.kt", 4),
            ),
            found,
        )
    }

    @Test
    fun `場所が記録されていない宣言は 数えない`() {
        val role = roleModel("domain.Service", layoutEntry("src/*Service.kt")).copy(declaredAt = null)

        assertEquals(emptyList<DeclarationAt>(), definitionModel(role).declarations(arch))
    }

    @Test
    fun `どの宣言にも 役割の module と役割が付く`() {
        val role = roleModel("domain.Service", layoutEntry("src/*Service.kt").declaredAt("ServiceRole.kt", 3)).declaredAt("ServiceRole.kt", 2)

        val found = definitionModel(role).declarations(arch)

        assertEquals(setOf(arch), found.map { it.module }.toSet())
        assertEquals(setOf("domain.Service"), found.map { it.role!!.qualifiedName }.toSet())
    }
}
