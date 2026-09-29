package com.example.sample.testing

import com.example.sample.data.user.UserRepository

/** In-memory [UserRepository] for tests of other modules. */
class FakeUserRepository(
    private var userName: String = "Test User",
) : UserRepository {
    override fun currentUserName(): String = userName
}
