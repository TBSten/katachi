@file:Suppress("DEPRECATION")

package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.camelCase
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.wildcards
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

/**
 * Three feature modules: `home` follows the rule, `debug-menu` follows it with a hyphen to fold,
 * and `settings` keeps its screen in another module's package. `home` also holds a screen
 * named after something other than the module.
 */
private fun featureModules(): FakeFileSystem = repositoryOf {
    "feature" {
        "home" {
            "build.gradle.kts"()
            "src" { "main" { "kotlin" { "com" { "example" { "feature" { "home" {
                "HomeScreen.kt"()
                "ProfileScreen.kt"()
            } } } } } } }
        }
        "debug-menu" {
            "build.gradle.kts"()
            "src" { "main" { "kotlin" { "com" { "example" { "feature" { "debugMenu" {
                "DebugMenuScreen.kt"()
            } } } } } } }
        }
        "settings" {
            "build.gradle.kts"()
            "src" { "main" { "kotlin" { "com" { "example" { "feature" { "home" {
                "SettingsScreen.kt"()
            } } } } } } }
        }
    }
}

/** An architecture with `gradle()` and one role, `Screen`, laid out by [block]. */
private fun screenArchitecture(block: LayoutScope.() -> Unit): Architecture = architectureOf {
    gradle(requireWrapper = false)
    "feature".group { "Screen".role { layout(block) } }
}

/** The files the role `Screen` owns in [featureModules], without build scripts, sorted. */
private fun screenFiles(arch: Architecture): List<String> =
    arch.process(featureModules()) { context ->
        context.filesOf(context.architecture.allRoles.single { it.name == "Screen" })
    }.filterNot { it.endsWith("build.gradle.kts") }.sorted()

class ModuleSugarMigrationSpec : FreeSpec({
    "非推奨のメッセージが勧める書き方は、.module { } と modulePackage と wildcards で書いた Role と同じソースを持つ" {
        val modulePackage = capitalizedModuleNamePackage("com.example")
        val before = screenArchitecture {
            ":feature:*".module {
                mainSourceSet / kotlin / modulePackage / "${wildcards[0].pascalCase}Screen".ktFile()
            }
        }
        val after = screenArchitecture {
            "feature" / capture("feature") / mainSourceSet / kotlin /
                "com/example/feature" / capture("feature").camelCase /
                "${capture("feature").pascalCase}Screen".ktFile()
        }

        screenFiles(after) shouldBe screenFiles(before)
        screenFiles(after).map { it.substringAfterLast('/') } shouldBe listOf("DebugMenuScreen.kt", "HomeScreen.kt")
    }
})
