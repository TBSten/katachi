package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.presentation.authoring.AuthoringStrings
import me.tbsten.katachi.intellij.presentation.authoring.PropertiesAuthoringStrings
import me.tbsten.katachi.intellij.presentation.gutter.GutterStrings
import me.tbsten.katachi.intellij.presentation.gutter.PropertiesGutterStrings

// The texts of the gutter and the definition side for the pure tests, read once from the properties. Platform-free: compiled into uiTest too.

/** English: what the IDE tests draw (the test IDE has no language pack), and what the pure tests compare with. */
internal val englishGutter: GutterStrings = PropertiesGutterStrings.english()

internal val englishAuthoring: AuthoringStrings = PropertiesAuthoringStrings.english()

/** Japanese: what the IDE shows under the Japanese language pack. */
internal val japaneseGutter: GutterStrings = PropertiesGutterStrings.japanese()

internal val japaneseAuthoring: AuthoringStrings = PropertiesAuthoringStrings.japanese()
