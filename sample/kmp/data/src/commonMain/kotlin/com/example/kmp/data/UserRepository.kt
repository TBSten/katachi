package com.example.kmp.data

/** Reads users. Implemented once for every platform-independent caller. */
interface UserRepository {
    fun names(): List<String>
}
