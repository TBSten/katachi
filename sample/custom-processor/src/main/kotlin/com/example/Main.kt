package com.example

import com.example.store.NoteStore

fun main() {
    val store = NoteStore()
    store.all().forEach { note -> println("${note.title}: ${note.body}") }
}
