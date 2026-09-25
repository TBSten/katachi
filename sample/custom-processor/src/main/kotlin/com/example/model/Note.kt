package com.example.model

/**
 * One note: a title and its body.
 *
 * ## Example 1: build a note
 * ```kt
 * val note = Note(title = "買い物", body = "牛乳とコーヒー豆")
 * note.title shouldBe "買い物"
 * ```
 */
data class Note(
    val title: String,
    val body: String,
)
