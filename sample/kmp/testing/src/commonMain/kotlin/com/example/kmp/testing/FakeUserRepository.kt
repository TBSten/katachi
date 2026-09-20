package com.example.kmp.testing

import com.example.kmp.data.UserRepository

/**
 * Test double used from the tests of the other modules. It lives in `commonMain` of a
 * production module rather than in a test source set so that every module's tests can
 * depend on it.
 */
class FakeUserRepository(private val names: List<String> = listOf("fake")) : UserRepository {
    override fun names(): List<String> = names
}
