package com.example.kmp.data.user

/**
 * The user agent the Android build sent before the `platform` package took over everything
 * platform specific, left in `androidMain` when katachi was adopted.
 *
 * Deliberately breaks the layout, as the demo of `baseline = baselineFile()`: `androidMain` of
 * `:data` only holds the `platform` package, so this `user` directory is an
 * `[UnexpectedDirectory]`. It is recorded in `katachi-baseline.json` and held back instead of
 * failing the test. Moving it into `platform` turns its entry stale until the baseline is pruned.
 */
internal fun androidUserAgent(): String = "katachi-sample-kmp/android"
