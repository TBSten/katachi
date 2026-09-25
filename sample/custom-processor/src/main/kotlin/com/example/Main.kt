package com.example

import com.example.store.NoteStore

/**
 * Prints every note the store holds.
 *
 * The whole application. It is here so that the `core/Entrypoint` role has a file to cover;
 * what this sample demonstrates is in `:architecture-test`.
 */
fun main() {
    val store = NoteStore()
    store.all().forEach { note -> println("${note.title}: ${note.body}") }
}
