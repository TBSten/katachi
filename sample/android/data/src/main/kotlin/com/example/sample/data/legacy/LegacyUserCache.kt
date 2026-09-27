package com.example.sample.data.legacy

/**
 * An in-memory cache of the user name from before `UserRepository` existed.
 *
 * Deliberately breaks the layout, as the demo of `baseline = baselineFile()`: `:data` only has
 * the `user` and `settings` packages, so the whole `legacy` directory is an
 * `[UnexpectedDirectory]`. It is recorded in `katachi-baseline.json` as one entry for the
 * directory and held back instead of failing the test. Deleting the directory turns that entry
 * stale until the baseline is pruned.
 */
class LegacyUserCache {
    private var userName: String? = null

    fun get(): String? = userName

    fun put(value: String) {
        userName = value
    }
}
