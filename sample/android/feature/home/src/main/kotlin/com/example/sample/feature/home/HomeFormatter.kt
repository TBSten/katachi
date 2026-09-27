package com.example.sample.feature.home

/**
 * Text formatting for the home screen, written before the `component` package existed and left
 * at the top of the feature package when katachi was adopted.
 *
 * Deliberately breaks the layout, as the demo of `baseline = baselineFile()`: a feature package
 * only holds `*Route.kt`, `*Screen.kt` and `*ViewModel.kt` at its top, so this file is an
 * `[UnexpectedFile]`. It is recorded in `katachi-baseline.json` and held back instead of failing
 * the test. Moving it into `component/` turns its entry stale until the baseline is pruned.
 */
internal object HomeFormatter {
    fun visitCountLabel(count: Int): String = "$count 回目の訪問"
}
