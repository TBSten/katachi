package me.tbsten.katachi.test.architecture.gradle

import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * The roles of the build definition.
 *
 * Build files are checked like everything else and are noise in the generated documentation,
 * so the whole group opts out with `documented = false`.
 *
 * What the build *writes* needs no role: `build/` and `.kotlin/` are in `.gitignore` and the
 * default `files = gitTracked()` never offers them. Every `.module { }` still injects
 * `"build".ignore()` of its own, so the reason stays readable under `files = wholeTree()` too.
 *
 * ## Why no `konsist { }` anywhere in this group
 *
 * Konsist 0.17.3 parses a file only when its name ends in `.kt`. Every file here is a `.kts`
 * script or a properties file, so a constraint would cover files and find none it can read —
 * which katachi reports as `KatachiKonsistNoKotlinFilesException` rather than passing quietly.
 */
fun ArchitectureScope.gradleRoles() {
    "build".group {
        documented = false
        title = "ビルド"

        "GradleRoot" {
            title = "ルートのビルド"
            summary = "ビルド全体の入口と Gradle wrapper"
            example("settings.gradle.kts", "サブプロジェクトの宣言")
            example("gradle/libs.versions.toml", "Kotlin / kotest / konsist のバージョンの唯一の出どころ")
            layout {
                // The root project of this build. `":".module { }` resolves to the repository
                // root and injects exactly two things there: `"build".ignore()` and
                // `build.gradle.kts`. sample/kmp avoids this spelling because its root project
                // is not the repository root; here the two coincide, and the root project
                // carries nothing but the sample aggregation tasks, so it is safe to use.
                ":".module {
                    "settings.gradle".ktsFile()
                    "gradle.properties".file()
                    "gradlew".file()
                    "gradlew.bat".file()
                    "gradle" {
                        "libs.versions.toml".file()
                        "wrapper" {
                            "gradle-wrapper.jar".file()
                            "gradle-wrapper.properties".file()
                        }
                    }
                }
            }
        }

        "GradleModule" {
            title = "モジュールのビルド"
            summary = "各モジュールが必ず持つ build.gradle.kts と、その生成物の非検査"
            example("katachi/build.gradle.kts", "ライブラリ本体のビルド定義")
            example("architecture-test/build.gradle.kts", "この定義を持つモジュールのビルド定義")
            layout {
                // Empty on purpose. The only thing this role has to say about a module is what
                // every module brings with it, and `.module { }` is precisely that: the
                // directory, its `build.gradle.kts`, and `"build".ignore()`. Where each
                // module's sources may live is the business of the roles that own them.
                ":katachi".module { }
                ":katachi-konsist".module { }
                ":architecture-test".module { }
            }
        }

        "BuildLogic" {
            title = "ビルドロジック"
            summary = "buildSrc の convention plugin。モジュール探索には出てこない別ビルド"
            example("kotlin-jvm.gradle.kts", "jvmToolchain(21) と useJUnitPlatform() を配る")
            layout {
                // Written as a plain directory rather than `":buildSrc".module { }`: buildSrc
                // is a build of its own, not a subproject of this one, so no module path
                // resolves to it. The two lines `.module { }` would have injected are spelled
                // out instead.
                "buildSrc" {
                    "build".ignore()
                    "build.gradle".ktsFile()
                    "settings.gradle".ktsFile()
                    "src" / "main" / "kotlin" / "*.gradle".ktsFile()
                }
            }
        }
    }
}
