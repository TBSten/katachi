package com.example.kmp.testing

import com.example.kmp.data.user.UserRepository

class FakeUserRepository(private val names: List<String> = listOf("fake")) : UserRepository {
    override fun names(): List<String> = names
}
