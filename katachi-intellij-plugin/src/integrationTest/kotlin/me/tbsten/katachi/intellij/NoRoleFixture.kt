package me.tbsten.katachi.intellij

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

/**
 * A copy of sample/jvm that is a git repository of its own (committed, as a user's project is) with one file added after
 * the commit that no role holds: the case the notification exists for. The file is under the `service` directory the
 * entry fixture gives a role, so the notification has a nearest role to name. Nothing here touches the original sample.
 *
 * ```kotlin
 * val project = NoRoleFixture(sample, workDir).prepare()
 * fixture.addToGit()   // what `git add` does for the user
 * ```
 */
internal class NoRoleFixture(sample: Path, workDir: Path) {
    private val base = EditorEntryFixture(sample, workDir)
    private lateinit var project: Path

    fun appBundle(ideHome: Path): Path = base.appBundle(ideHome)

    fun prepare(): Path {
        project = base.prepare()
        git("init", "-q")
        git("add", "-A")
        git("-c", "user.name=katachi-smoke", "-c", "user.email=smoke@example.com", "commit", "-q", "-m", "baseline")
        project.resolve(STRAY).also { Files.createDirectories(it.parent) }.writeText("package com.example.service.helpers\n\nclass NoRoleProbe\n")
        return project
    }

    /** `git add` for the stray file only. */
    fun addToGit() = git("add", STRAY)

    private fun git(vararg arguments: String) {
        val process = ProcessBuilder("git", *arguments).directory(project.toFile()).inheritIO().start()
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            error("Timed out: git ${arguments.joinToString(" ")}")
        }
        check(process.exitValue() == 0) { "Failed: git ${arguments.joinToString(" ")}" }
    }

    companion object {
        /** Under `service`, which the entry fixture's role declares; `helpers` is a directory no role knows. */
        const val STRAY = "src/main/kotlin/com/example/service/helpers/NoRoleProbe.kt"
    }
}
