package com.example.tool

import com.example.tool.documentation
import com.example.tool.git
import com.example.tool.ideCodeStyle
import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.toolGroup() = "Tool".group {
    documented = false
    title = "Tool configuration"
    summary = "Configuration of tools around the project that are not the build itself"

    description = """
        Things kept in the repository that support development but are not the build. For now
        this holds three roles: the Git configuration (`.gitignore`), the `README.md` that people
        read, and the code style IntelliJ IDEA shares through `.idea/codeStyles/`.

        Like the build, it is `documented = false`: it is checked but does not appear in the
        generated documentation. When editor or CI configuration files appear, the intended way to
        grow is to add one role to this group rather than mixing them into the `.gitignore` role.
    """.trimIndent()

    documentation()
    git()
    ideCodeStyle()
}
