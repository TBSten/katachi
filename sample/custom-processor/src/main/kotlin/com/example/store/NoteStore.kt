package com.example.store

import com.example.model.Note

/**
 * Where notes come from, kept in memory.
 *
 * ## Example 1: read every note
 * ```kt
 * NoteStore().all().size shouldBe 2
 * ```
 */
class NoteStore {
    private val notes = listOf(
        Note(title = "買い物", body = "牛乳とコーヒー豆"),
        Note(title = "読書", body = "katachi の README を読む"),
    )

    /** Every note, in the order they were added. */
    fun all(): List<Note> = notes
}
