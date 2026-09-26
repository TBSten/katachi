package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.testSourceSet

/**
 * The role of `:katachi-gradle-plugin`, the Gradle plugin katachi publishes.
 *
 * ## Why the package paths are written out instead of going through `mainPackage`
 *
 * `mainPackage` and `testPackage` map a module path to a directory below `src/main/kotlin` and
 * `src/test/kotlin`. This module has no Kotlin in it at all — see its `build.gradle.kts` for
 * why — so neither table applies to it. One literal path per source set says it once, in the
 * file it applies to.
 *
 * ## Why there is no `konsist { }` here
 *
 * Konsist 0.17.3 parses a file only when its name ends in `.kt`. Every file this role owns is a
 * `.java` file, so a constraint written here would cover files and find none it can read —
 * which katachi reports as `KatachiKonsistNoKotlinFilesException` rather than passing quietly.
 * The same reason keeps `konsist { }` out of the whole `Gradle` group.
 */
fun DeclarationContainerScope.gradlePlugin() = "GradlePlugin" {
    title = "プラグイン本体"
    summary = "processor ごとのタスク（katachiDocs など）を登録する plugin。利用者の Gradle デーモンに読まれるので Java で書く"
    example("KatachiPlugin.java", "plugin 本体。登録キーごとに katachi<Key> タスクを登録する")
    example("KatachiProcessorTask.java", "processor を1つ決め打ちにし、test の runtimeClasspath で JVM を起動するタスク")
    example("KatachiExtension.java", "利用者が書く katachi { } ブロック")
    example("GenerateKatachiEntryPointTask.java", "architecture の参照とレジストリだけを吐くコード生成")
    layout {
        ":katachi-gradle-plugin".module {
            mainSourceSet / "java" / "me/tbsten/katachi/gradle" / "*.java".file()
            testSourceSet / "java" / "me/tbsten/katachi/test/gradle" / "*.java".file()
        }
    }
}
