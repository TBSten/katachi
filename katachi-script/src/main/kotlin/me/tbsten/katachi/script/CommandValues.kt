package me.tbsten.katachi.script

/**
 * The test framework `katachi scaffold --test-framework` lays the test module out for.
 *
 * The top-level [kotest] and [junit5] are the same values, so a script can write them bare.
 *
 * ## Example 1: pick Kotest for the generated tests
 *
 * ```kt
 * katachi.scaffold.`test-framework`(kotest)
 * katachi.scaffold.`test-framework`(TestFramework.Kotest) // the same
 * ```
 *
 * @property value How the command line spells it.
 * @see ScaffoldCommand
 */
public enum class TestFramework(public val value: String) {
    /** JUnit 5 (`junit5`). */
    Junit5("junit5"),

    /** Kotest (`kotest`). */
    Kotest("kotest"),
}

/** [TestFramework.Kotest], to write `` `test-framework`(kotest) ``. */
public val kotest: TestFramework = TestFramework.Kotest

/** [TestFramework.Junit5], to write `` `test-framework`(junit5) ``. */
public val junit5: TestFramework = TestFramework.Junit5

/**
 * How `katachi scaffold --deps` writes the dependencies of the test module.
 *
 * ## Example 1: write the dependencies into the version catalog
 *
 * ```kt
 * katachi.scaffold.deps(catalog)
 * ```
 *
 * @property value How the command line spells it.
 * @see ScaffoldCommand
 */
public enum class DependencyStyle(public val value: String) {
    /** Adds the libraries to `gradle/libs.versions.toml` and refers to them as `libs.katachi` (`catalog`). */
    Catalog("catalog"),

    /** Writes the coordinates into `build.gradle.kts` (`literal`). */
    Literal("literal"),
}

/** [DependencyStyle.Catalog], to write `deps(catalog)`. */
public val catalog: DependencyStyle = DependencyStyle.Catalog

/** [DependencyStyle.Literal], to write `deps(literal)`. */
public val literal: DependencyStyle = DependencyStyle.Literal

/**
 * The language `katachi init --lang` records for the check list, the report and the command's own messages.
 *
 * ## Example 1: set up the working directory in Japanese
 *
 * ```kt
 * katachi.init.lang(ja)
 * ```
 *
 * @property value How the command line spells it.
 * @see InitCommand
 */
public enum class Lang(public val value: String) {
    /** Japanese (`ja`). */
    Ja("ja"),

    /** English (`en`). */
    En("en"),
}

/** [Lang.Ja], to write `lang(ja)`. */
public val ja: Lang = Lang.Ja

/** [Lang.En], to write `lang(en)`. */
public val en: Lang = Lang.En
