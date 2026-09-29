package com.example.model

/**
 * One note: a title and its body.
 *
 * ## Example 1: build a note
 * ```kt
 * val note = Note(title = "Groceries", body = "Milk and coffee beans")
 * note.title shouldBe "Groceries"
 * ```
 */
data class Note(
    val title: String,
    val body: String,
)
