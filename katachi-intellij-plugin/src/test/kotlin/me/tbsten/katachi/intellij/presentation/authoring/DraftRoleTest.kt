package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The role a live template is expanded for: the one the definition declared in the open file. */
class DraftRoleTest {
    private val file = "ServiceRole.kt"
    private fun role(name: String, line: Int, site: String = file) = roleModel("domain.$name", layoutEntry("src/*.kt")).declaredAt(site, line)

    private fun snapshots(vararg roles: me.tbsten.katachi.intellij.model.RoleModel) = listOf(definitionSnapshot(definitionModel(*roles)))

    @Test
    fun `the role declared in the open file is the one`() {
        val found = draftRoleOf(file, 5, snapshots(role("Service", 2), role("Other", 2, "OtherRole.kt"))) { _, _ -> true }

        assertEquals("domain.Service", found?.role?.qualifiedName)
    }

    @Test
    fun `of two roles in one file the nearer to the caret is the one`() {
        val found = draftRoleOf(file, 20, snapshots(role("First", 2), role("Second", 18))) { _, _ -> true }

        assertEquals("domain.Second", found?.role?.qualifiedName)
    }

    @Test
    fun `a role of a file of the same name elsewhere is not the one`() {
        assertNull(draftRoleOf(file, 2, snapshots(role("Service", 2))) { _, _ -> false })
    }

    @Test
    fun `a file that declares no role has none`() {
        assertNull(draftRoleOf("Plain.kt", 2, snapshots(role("Service", 2))) { _, _ -> true })
    }
}
