package com.example.kmp.data.user

/** Reads users. Implemented once for every platform-independent caller. */
interface UserRepository {
    fun names(): List<String>
}
