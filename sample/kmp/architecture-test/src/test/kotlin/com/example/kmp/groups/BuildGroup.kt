package com.example.kmp.groups

import com.example.kmp.roles.gradleModule
import com.example.kmp.roles.gradleRoot
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Build scripts. Not documented: they are part of the repository's shape but not part of the
 * architecture a reader of the docs needs.
 *
 * `documented = false` is written on the group and on each role, because katachi keeps the
 * declared value as written and does not inherit it from the parent.
 *
 * Both roles also carry `owner = "platform"`, this sample's own metadata key (see
 * [com.example.kmp.processor.Owner]). It is not part of katachi -- it exists only to give
 * `com.example.kmp.processor.PlatformOwnedFilesProcessor` something to read, and to demonstrate
 * that a user of katachi can bring their own vocabulary the same way `documented` and `summary`
 * do. `tool/Git` carries it too, which is why the key is documented where it is declared rather
 * than here.
 *
 * No role for `build/`, `.kotlin/` or `local.properties`. They exist on a developer machine and
 * on CI but are in none of the commits, and `files` is left at its default `gitTracked()`, so
 * git is the one deciding which files this project has -- and it never reports an ignored file.
 * Nothing has to be declared to keep them out.
 */
fun DeclarationContainerScope.buildGroup() = "build".group {
    documented = false
    title = "ビルド"

    gradleModule()
    gradleRoot()
}
