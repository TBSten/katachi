package me.tbsten.katachi.intellij.presentation.rules

// Mirrors katachi's `OsWrittenFiles`: files a file manager writes into a folder on its own. A role for one passes on the
// machine that writes it and fails with MissingFile on a checkout without it (CI), so the plugin sends it to .gitignore.
// The case is ignored because Windows writes Desktop.ini too.
private val OS_WRITTEN_FILE_NAMES: Set<String> = sortedSetOf(String.CASE_INSENSITIVE_ORDER, ".DS_Store", "Thumbs.db", "desktop.ini")

internal fun isOsWrittenFile(relativePath: String): Boolean = relativePath.substringAfterLast('/') in OS_WRITTEN_FILE_NAMES
