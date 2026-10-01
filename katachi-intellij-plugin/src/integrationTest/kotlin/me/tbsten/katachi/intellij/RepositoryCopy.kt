package me.tbsten.katachi.intellij

/**
 * The `rsync` options every fixture uses to copy the katachi repository for the IDE under test:
 * everything but build output, IDE and Gradle state, the plugin itself, and contributors' local
 * work (`.local/`, agents' worktrees, `node_modules/`). sample/jvm includes the whole repository,
 * so the IDE indexes all of the copy: with `.local/` (15 GB at the time) it ran out of heap.
 *
 * ```kotlin
 * run(workDir, "rsync", "-a", *REPOSITORY_COPY_EXCLUDES, "$repository/", "$destination/")
 * ```
 */
internal val REPOSITORY_COPY_EXCLUDES: Array<String> = listOf(
    "build/", ".gradle/", ".kotlin/", ".idea/", ".git", ".intellijPlatform/",
    "/katachi-intellij-plugin/", "/.local/", "/.claude/worktrees/", "node_modules/",
).flatMap { listOf("--exclude", it) }.toTypedArray()
