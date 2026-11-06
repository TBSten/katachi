// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

/**
 * The language `katachi init --lang` records for the check list, the report and the command's own messages.
 *
 * The top-level [en] and [ja] are the same values, so a script can write them bare.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.doctor.lang(ja)
 * katachi.doctor.lang(Lang.Ja) // the same
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class Lang(public val value: String) {
    /** English (`en`). */
    En("en"),

    /** Japanese (`ja`). */
    Ja("ja"),
}

/** [Lang.En], to write `en` bare. */
public val en: Lang = Lang.En

/** [Lang.Ja], to write `ja` bare. */
public val ja: Lang = Lang.Ja

/**
 * The test framework `katachi scaffold --test-framework` lays the test module out for.
 *
 * The top-level [junit5] and [kotest] are the same values, so a script can write them bare.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.scaffold.`test-framework`(kotest)
 * katachi.scaffold.`test-framework`(TestFramework.Kotest) // the same
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class TestFramework(public val value: String) {
    /** JUnit 5 (`junit5`). */
    Junit5("junit5"),

    /** Kotest (`kotest`). */
    Kotest("kotest"),
}

/** [TestFramework.Junit5], to write `junit5` bare. */
public val junit5: TestFramework = TestFramework.Junit5

/** [TestFramework.Kotest], to write `kotest` bare. */
public val kotest: TestFramework = TestFramework.Kotest

/**
 * How `katachi scaffold --deps` writes the dependencies of the test module.
 *
 * The top-level [catalog] and [literal] are the same values, so a script can write them bare.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.scaffold.deps(catalog)
 * katachi.scaffold.deps(DependencyStyle.Catalog) // the same
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class DependencyStyle(public val value: String) {
    /** Adds the libraries to `gradle/libs.versions.toml` and refers to them as `libs.katachi` (`catalog`). */
    Catalog("catalog"),

    /** Writes the coordinates into `build.gradle.kts` (`literal`). */
    Literal("literal"),
}

/** [DependencyStyle.Catalog], to write `catalog` bare. */
public val catalog: DependencyStyle = DependencyStyle.Catalog

/** [DependencyStyle.Literal], to write `literal` bare. */
public val literal: DependencyStyle = DependencyStyle.Literal

/**
 * The module whose API reference `katachi docs --api` fetches.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.docs.api(ApiModule.Katachi)
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class ApiModule(public val value: String) {
    /** katachi itself (`katachi`). */
    Katachi("katachi"),

    /** katachi-konsist (`katachi-konsist`). */
    KatachiKonsist("katachi-konsist"),
}

/**
 * Whether `katachi preferences set constraintNames=...` puts the question number in constraint names.
 *
 * The top-level [plain] and [numbered] are the same values, so a script can write them bare.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.preferences.set.constraintNames(plain)
 * katachi.preferences.set.constraintNames(ConstraintNames.Plain) // the same
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class ConstraintNames(public val value: String) {
    /** Names only (`plain`). */
    Plain("plain"),

    /** Names with the question number (`numbered`). */
    Numbered("numbered"),
}

/** [ConstraintNames.Plain], to write `plain` bare. */
public val plain: ConstraintNames = ConstraintNames.Plain

/** [ConstraintNames.Numbered], to write `numbered` bare. */
public val numbered: ConstraintNames = ConstraintNames.Numbered

/**
 * Which file `katachi answers --target` reads or writes.
 *
 * The top-level [report] and [checkList] are the same values, so a script can write them bare.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.answers.show.target(report)
 * katachi.answers.show.target(Target.Report) // the same
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class Target(public val value: String) {
    /** The report (`report`). */
    Report("report"),

    /** The check list (`check-list`). */
    CheckList("check-list"),
}

/** [Target.Report], to write `report` bare. */
public val report: Target = Target.Report

/** [Target.CheckList], to write `checkList` bare. */
public val checkList: Target = Target.CheckList
