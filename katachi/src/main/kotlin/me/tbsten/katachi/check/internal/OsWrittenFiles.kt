package me.tbsten.katachi.check.internal

// Files a file manager writes into a folder on its own: Finder writes .DS_Store (view settings) into every folder it
// opens, File Explorer writes Thumbs.db (a thumbnail cache) and desktop.ini (a folder's icon and view). Only names no
// project gives a file of its own, so a file the project keeps is never sent to .gitignore. The case is ignored because
// Windows writes Desktop.ini too, and its file system takes the two for one name.
private val OS_WRITTEN_FILES: Map<String, String> = sortedMapOf(
    String.CASE_INSENSITIVE_ORDER,
    ".DS_Store" to "Finder on macOS",
    "Thumbs.db" to "File Explorer on Windows",
    "desktop.ini" to "File Explorer on Windows",
)

/**
 * The rest of an `[UnexpectedFile]` block for a file the OS writes on its own, or `null` for any other file.
 *
 * The report does not know what `files` holds, which is why the fix names both selections.
 */
internal fun osWrittenFileLines(path: String): List<String>? {
    val name = path.substringAfterLast('/')
    val writer = OS_WRITTEN_FILES[name] ?: return null
    return listOf(
        "$STEP$writer writes it on its own: deleting it does not last, and a role for it fails the check " +
            "with [MissingFile] on a checkout without it, such as on CI.",
        "",
        "${STEP}How to fix:",
        "$STEP$STEP- Add `$name` to .gitignore: files = gitTracked() (the default) does not look at a file git ignores",
        "$STEP$STEP- If it was committed, also untrack it with `git rm --cached`: .gitignore does not apply to a file git already tracks",
        "$STEP$STEP- If files = wholeTree(), which does not read .gitignore, also switch to files = gitTracked()",
    )
}
