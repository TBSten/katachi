package com.example.sample.data.legacy

class LegacyUserCache {
    private var userName: String? = null

    fun get(): String? = userName

    fun put(value: String) {
        userName = value
    }
}
