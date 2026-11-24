package me.tbsten.katachi.intellij.presentation.authoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The `ktFileContent { }` lines drafted from the files of a role (ide-live-template-from-definition). */
class FileContentDraftsTest {
    private fun file(
        baseName: String,
        packageName: String = "com.example.service",
        imports: List<String> = emptyList(),
        vararg declarations: SampleDeclaration,
    ) = SampleFile(baseName, packageName, imports, declarations.toList())

    private fun cls(name: String, vararg modifiers: String, members: Boolean = false) = SampleDeclaration(modifiers.toList(), "class", name, members)

    @Test
    fun `no file gives no draft`() {
        assertNull(FileContentDrafts.of(emptyList()))
    }

    @Test
    fun `one file gives its package, its imports and its declarations as they are`() {
        val draft = FileContentDrafts.of(
            listOf(file("UserService", imports = listOf("kotlinx.coroutines.flow.Flow", "com.example.User"), declarations = arrayOf(cls("UserService")))),
        )!!

        assertEquals("com.example.service", draft.packagePattern)
        assertEquals(listOf("`import`(\"com.example.User\")", "`import`(\"kotlinx.coroutines.flow.Flow\")"), draft.importLines)
        assertEquals(listOf("required(`class`, fileName)"), draft.declarationLines)
    }

    @Test
    fun `packages are cut to what they share and end with two stars`() {
        assertEquals("com.example.**", FileContentDrafts.packagePatternOf(listOf("com.example.a", "com.example.b.c")))
        assertEquals("com.example.a", FileContentDrafts.packagePatternOf(listOf("com.example.a", "com.example.a")))
    }

    @Test
    fun `packages that share nothing, and a file with no package, give two stars`() {
        assertEquals("**", FileContentDrafts.packagePatternOf(listOf("com.a", "org.b")))
        assertEquals("**", FileContentDrafts.packagePatternOf(listOf("")))
        assertEquals("**", FileContentDrafts.packagePatternOf(listOf("com.a", "")))
    }

    @Test
    fun `many imports are grouped under their first two names`() {
        val imports = (1..10).map { "kotlinx.coroutines.flow.Name$it" } + "com.example.User"

        val draft = FileContentDrafts.of(listOf(file("A", imports = imports, declarations = arrayOf(cls("A")))))!!

        assertEquals(listOf("`import`(\"com.example.**\")", "`import`(\"kotlinx.coroutines.**\")"), draft.importLines)
    }

    @Test
    fun `too many groups fall to the first name`() {
        val imports = (1..14).map { "kotlinx.part$it.Name" }

        val draft = FileContentDrafts.of(listOf(file("A", imports = imports, declarations = arrayOf(cls("A")))))!!

        assertEquals(listOf("`import`(\"kotlinx.**\")"), draft.importLines)
    }

    @Test
    fun `an import with a backquote or a dollar is left out`() {
        val draft = FileContentDrafts.of(listOf(file("A", imports = listOf("a.`in`.B", "a.b.C"), declarations = arrayOf(cls("A")))))!!

        assertEquals(listOf("`import`(\"a.b.C\")"), draft.importLines)
    }

    @Test
    fun `modifiers are written as the source has them, public and final left out`() {
        val draft = FileContentDrafts.of(
            listOf(file("Color", declarations = arrayOf(cls("Color", "public", "final", "data", "private")))),
        )!!

        assertEquals(listOf("required(private, data, `class`, fileName)"), draft.declarationLines)
    }

    @Test
    fun `a modifier katachi has no word for is left out`() {
        val draft = FileContentDrafts.of(listOf(file("A", declarations = arrayOf(cls("A", "vararg", "sealed")))))!!

        assertEquals(listOf("required(sealed, `class`, fileName)"), draft.declarationLines)
    }

    @Test
    fun `a class with members allows anything else inside, and one without writes no braces`() {
        val draft = FileContentDrafts.of(
            listOf(file("A", declarations = arrayOf(cls("A", members = true), SampleDeclaration(emptyList(), "val", "limit", false)))),
        )!!

        assertEquals(
            listOf("required(`class`, fileName) {\n    allow(anythingElse)\n}", "allow(`val`, \"limit\")"),
            draft.declarationLines,
        )
    }

    @Test
    fun `a function has no braces however it is written`() {
        val draft = FileContentDrafts.of(listOf(file("Routing", declarations = arrayOf(SampleDeclaration(emptyList(), "fun", "configureRouting", false)))))!!

        assertEquals(listOf("allow(`fun`, \"configureRouting\")"), draft.declarationLines)
    }

    @Test
    fun `a declaration every file has under the file's name is required, one only some have is allowed`() {
        val draft = FileContentDrafts.of(
            listOf(
                file("A", declarations = arrayOf(cls("A"), cls("Helper"))),
                file("B", declarations = arrayOf(cls("B"))),
            ),
        )!!

        assertEquals(listOf("required(`class`, fileName)", "allow(`class`, \"Helper\")"), draft.declarationLines)
    }

    @Test
    fun `members are kept when any one file has them`() {
        val draft = FileContentDrafts.of(
            listOf(
                file("A", declarations = arrayOf(cls("A", members = true))),
                file("B", declarations = arrayOf(cls("B", members = false))),
            ),
        )!!

        assertEquals(listOf("required(`class`, fileName) {\n    allow(anythingElse)\n}"), draft.declarationLines)
    }

    @Test
    fun `many names are written for any name but the file's own`() {
        val helpers = (1..14).map { cls("Helper$it") }
        val draft = FileContentDrafts.of(listOf(file("A", declarations = (listOf(cls("A")) + helpers).toTypedArray())))!!

        assertEquals(listOf("required(`class`, fileName)", "allow(`class`, \"*\")"), draft.declarationLines)
    }

    @Test
    fun `a kind the draft cannot write is left out`() {
        val draft = FileContentDrafts.of(listOf(file("A", declarations = arrayOf(SampleDeclaration(emptyList(), "enum entry", "X", false)))))!!

        assertEquals(emptyList<String>(), draft.declarationLines)
    }
}
