package com.example.sample.data.user

/**
 * Production implementation of [UserRepository].
 *
 * There is no backend behind this sample, so the value is hard-coded. What matters for
 * katachi is that the interface and the implementation are two files with two names.
 */
class UserRepositoryImpl : UserRepository {
    override fun currentUserName(): String = "katachi"
}
