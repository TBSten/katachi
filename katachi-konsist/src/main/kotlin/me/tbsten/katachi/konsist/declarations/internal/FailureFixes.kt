package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintFix

internal fun allowItFix(site: DeclarationSite, code: String): FileConstraintFix =
    FileConstraintFix("Allow it in ktFileContent { } at $site:", code = listOf(code))

internal fun exceptFix(text: String, name: String): FileConstraintFix = exceptFixWithCode(text, ".except(${kotlinString(name)})")

internal fun exceptFixWithCode(text: String, code: String): FileConstraintFix = FileConstraintFix(text, code = listOf(code))

internal fun onlyNotLinesFix(what: String, site: DeclarationSite, code: String): FileConstraintFix = FileConstraintFix(
    "Only not. lines are written for $what, so every other one is rejected too. To allow the rest, add at $site:",
    code = listOf(code),
)

internal fun DeclarationListRules.allowsOnlyByNot(): Boolean =
    lines.isNotEmpty() && lines.all { it.prohibited } && anythingElse.isEmpty()

internal fun kotlinString(text: String): String =
    "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

// Escape every character the glob reads: a name such as `is it {ok}?` would otherwise be refused or read too widely.
internal fun namePatternLiteral(name: String): String =
    kotlinString(buildString { for (char in name) append(if (char in GLOB_CHARACTERS) "\\$char" else "$char") })

private const val GLOB_CHARACTERS: String = "*\\{}?[],"
