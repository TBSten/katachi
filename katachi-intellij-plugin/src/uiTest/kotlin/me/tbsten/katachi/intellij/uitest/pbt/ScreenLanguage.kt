package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import me.tbsten.katachi.intellij.presentation.EnglishKatachiStrings
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiStrings

/**
 * The language a [ScreenMachine] words and draws the tool window in. The texts differ in length and
 * wrap differently, so the properties draw both; an enum rather than the strings themselves, so a
 * failure prints which one.
 */
internal enum class ScreenLanguage(val strings: KatachiStrings) {
    Japanese(JapaneseKatachiStrings),
    English(EnglishKatachiStrings),
}

/** Either language, one per sequence. */
internal val languageArb: Arb<ScreenLanguage> = Arb.enum<ScreenLanguage>()
