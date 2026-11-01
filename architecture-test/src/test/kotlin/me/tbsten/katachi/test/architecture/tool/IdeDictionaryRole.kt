package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the spelling dictionary IntelliJ IDEA shares through the repository.
 *
 * `.gitignore` ignores everything below `.idea` and takes `dictionaries/project.xml` back, so
 * git tracks it and `files = gitTracked()` hands it to the check like any other file.
 */
fun DeclarationContainerScope.ideDictionary() = "IdeDictionary".role {
    title = "IDE の辞書"
    summary = "IntelliJ IDEA のスペルチェックに登録した語"
    example(".idea/dictionaries/project.xml", "この辞書に無い語を IDE が警告しないようにする")
    layout {
        ".idea" / "dictionaries" / "project.xml".file()
    }
}
