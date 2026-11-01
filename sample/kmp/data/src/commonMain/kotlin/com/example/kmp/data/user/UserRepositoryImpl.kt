package com.example.kmp.data.user

class UserRepositoryImpl : UserRepository {
    override fun names(): List<String> = listOf("alice", "bob")
}
