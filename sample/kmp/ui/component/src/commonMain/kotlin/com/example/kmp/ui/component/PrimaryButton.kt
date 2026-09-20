package com.example.kmp.ui.component

import com.example.kmp.ui.theme.AppTheme

/** A shared UI building block. */
class PrimaryButton(private val label: String) {
    fun render(): String = "[$label @${AppTheme.PRIMARY_COLOR}]"
}
