package com.example.store

import com.example.model.Note

class NoteStore {
    private val notes = listOf(
        Note(title = "Groceries", body = "Milk and coffee beans"),
        Note(title = "Reading", body = "Read the katachi README"),
    )

    fun all(): List<Note> = notes
}
