package com.example.sample.data.user

/** Reads and writes users. */
interface UserRepository {
    /** Name of the signed-in user, shown by the home screen. */
    fun currentUserName(): String
}
