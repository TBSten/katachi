// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.option.ConstraintNames
import me.tbsten.katachi.script.option.DependencyStyle
import me.tbsten.katachi.script.option.Lang
import me.tbsten.katachi.script.option.Target
import me.tbsten.katachi.script.option.TestFramework

/** [Lang.En], to write `en` bare. */
public val en: Lang = Lang.En

/** [Lang.Ja], to write `ja` bare. */
public val ja: Lang = Lang.Ja

/** [TestFramework.Junit5], to write `junit5` bare. */
public val junit5: TestFramework = TestFramework.Junit5

/** [TestFramework.Kotest], to write `kotest` bare. */
public val kotest: TestFramework = TestFramework.Kotest

/** [DependencyStyle.Catalog], to write `catalog` bare. */
public val catalog: DependencyStyle = DependencyStyle.Catalog

/** [DependencyStyle.Literal], to write `literal` bare. */
public val literal: DependencyStyle = DependencyStyle.Literal

/** [ConstraintNames.Plain], to write `plain` bare. */
public val plain: ConstraintNames = ConstraintNames.Plain

/** [ConstraintNames.Numbered], to write `numbered` bare. */
public val numbered: ConstraintNames = ConstraintNames.Numbered

/** [Target.Report], to write `report` bare. */
public val report: Target = Target.Report

/** [Target.CheckList], to write `checkList` bare. */
public val checkList: Target = Target.CheckList
