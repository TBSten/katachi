package com.example.tool

import com.example.data.repository
import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the code style IntelliJ IDEA shares through the repository. */
fun DeclarationContainerScope.ideCodeStyle() = "IdeCodeStyle".role {
    title = "IDE code style"
    summary = "The code style IntelliJ IDEA shares through the repository"
    description = """
        The two files under `.idea/codeStyles/` that make everyone's IDE format the code the same
        way. `.gitignore` keeps the rest of `.idea/` out of Git (`.idea/*`) and takes this one
        directory back (`!.idea/codeStyles/`), which is the shape Android Studio and IntelliJ IDEA
        suggest themselves.

        Because the files are tracked, `files = gitTracked()` hands them to katachi like any other
        file, and they need a role. Nothing else below `.idea/` is tracked, so nothing else below
        it is checked. A project that would rather not look into `.idea/` at all writes
        `".idea".ignore()` instead of this role.
    """.trimIndent()
    forbiddenContents = "Per-machine IDE state such as `workspace.xml`: it stays ignored and is never tracked."
    example(".idea/codeStyles/Project.xml", "The shared code style scheme")
    example(".idea/codeStyles/codeStyleConfig.xml", "Tells the IDE to use the project scheme")
    layout {
        ".idea" / "codeStyles" {
            "Project.xml".file()
            "codeStyleConfig.xml".file()
        }
    }
}
