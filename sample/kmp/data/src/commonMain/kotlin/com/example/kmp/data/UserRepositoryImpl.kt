package com.example.kmp.data

/** The real implementation. A stub: the sample only cares about where files live. */
class UserRepositoryImpl : UserRepository {
    override fun names(): List<String> = listOf("alice", "bob")
}
