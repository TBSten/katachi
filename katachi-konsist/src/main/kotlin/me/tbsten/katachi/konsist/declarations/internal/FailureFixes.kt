package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintFix

/*
 * The `How to fix:` items more than one kind of failure gives, and the code they show. Code is
 * shown to be pasted into the role's `ktFileContent { }`, so a name in it is written as the
 * pattern that matches only that name.
 */

internal fun allowItFix(site: DeclarationSite, code: String): FileConstraintFix =
    FileConstraintFix("Allow it in ktFileContent { } at $site:", code = listOf(code))

internal fun exceptFix(text: String, name: String): FileConstraintFix = exceptFixWithCode(text, ".except(${kotlinString(name)})")

internal fun exceptFixWithCode(text: String, code: String): FileConstraintFix = FileConstraintFix(text, code = listOf(code))

/**
 * The fix that comes first when the lines of one kind ([what]: `imports`, `packages`,
 * `declarations`) are all `not.` lines: such lines still make an allow list, so everything they
 * do not prohibit is rejected too, which [code] written at [site] lets through.
 */
internal fun onlyNotLinesFix(what: String, site: DeclarationSite, code: String): FileConstraintFix = FileConstraintFix(
    "Only not. lines are written for $what, so every other one is rejected too. To allow the rest, add at $site:",
    code = listOf(code),
)

/** Whether every line of [this] is a `not.` line, and nothing else is allowed either. */
internal fun DeclarationListRules.allowsOnlyByNot(): Boolean =
    lines.isNotEmpty() && lines.all { it.prohibited } && anythingElse.isEmpty()

/** [text] as a Kotlin string literal. */
internal fun kotlinString(text: String): String =
    "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

/**
 * [name] as the Kotlin string of a name pattern that matches it and nothing else: every character
 * the glob reads (`*`, `?`, `{` and the like) escaped with `\`. A backticked test name such as
 * `is it {ok}?` is a name the glob would otherwise refuse, or read too widely.
 */
internal fun namePatternLiteral(name: String): String =
    kotlinString(buildString { for (char in name) append(if (char in GLOB_CHARACTERS) "\\$char" else "$char") })

/** The characters katachi's glob reads, and so escapes with `\` to mean themselves. */
private const val GLOB_CHARACTERS: String = "*\\{}?[],"
