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
        Note(title = "Groceries", body = "Milk and coffee beans"),
        Note(title = "Reading", body = "Read the katachi README"),
    )

    /** Every note, in the order they were added. */
    fun all(): List<Note> = notes
}
