package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.FsPath
import me.tbsten.katachi.check.ModuleIndex
import me.tbsten.katachi.check.ModulePath
import me.tbsten.katachi.check.ModulePattern
import me.tbsten.katachi.check.ModuleResolver
import me.tbsten.katachi.check.discoverModules
import me.tbsten.katachi.check.moduleIndex
import me.tbsten.katachi.dsl.InternalKatachiApi

@OptIn(InternalKatachiApi::class)
class ModuleResolverSpec : FreeSpec({
    /**
     * A build shaped like the KMP sample: nested modules, a `feature/` that grows, a
     * directory Gradle knows nothing about (`app/ios`), and an included build.
     */
    fun sampleTree() = fakeFileSystem(workingDirectory = "/repo") {
        "/repo" {
            "settings.gradle.kts"()
            "build.gradle.kts"()
            "gradlew"()
            ".git" { "HEAD"() }
            "app" {
                "android" { "build.gradle.kts"(); "src/commonMain" { "App.kt"() } }
                "ios" { "iosApp" { "Info.plist"() } }
            }
            "core" {
                "data" {
                    "build.gradle.kts"()
                    "remoteApi" { "build.gradle.kts"() }
                }
            }
            "feature" {
                "home" { "build.gradle.kts"() }
                "settings" { "build.gradle"() }
                "debug-menu" { "build.gradle.kts"() }
            }
            "buildLogic" {
                "settings.gradle.kts"()
                "convention" { "build.gradle.kts"() }
            }
            "docs" { "README.md"() }
        }
    }

    val root = FsPath.of("/repo")

    fun index(): ModuleIndex = moduleIndex(sampleTree(), root)

    fun expand(key: String) = index().expand(ModulePattern.compile(key))

    "規約ベースの解決" - {
        "\":core:data\" は core/data になる" {
            ModuleResolver.Conventional.directoryOf(ModulePath.of(":core:data")) shouldBe "core/data"
        }

        "3階層のネストモジュールも階層どおりに解決される" {
            ModuleResolver.Conventional.directoryOf(ModulePath.of(":core:data:remoteApi")) shouldBe
                "core/data/remoteApi"
        }

        "先頭の : の有無で解決先は変わらない" {
            val withColon = ModuleResolver.Conventional.directoryOf(ModulePath.of(":core:data"))
            val withoutColon = ModuleResolver.Conventional.directoryOf(ModulePath.of("core:data"))
            withoutColon shouldBe withColon
        }

        "ルートプロジェクトはプロジェクトルートそのものになる" {
            ModuleResolver.Conventional.directoryOf(ModulePath.ROOT) shouldBe ""
        }
    }

    "moduleResolver の差し替え" - {
        "規約と異なるディレクトリを配置場所にできる" {
            val resolver = ModuleResolver { module ->
                if (module.value == ":app:android") "apps/android" else module.segments.joinToString("/")
            }
            val custom = moduleIndex(sampleTree(), root, resolver)

            custom.resolve(ModulePath.of(":app:android")).directory shouldBe "apps/android"
            custom.resolve(ModulePath.of(":core:data")).directory shouldBe "core/data"
        }

        "余分な区切りを付けて返しても layout のパスとして揃えられる" {
            val resolver = ModuleResolver { module -> "/apps/${module.name}/" }
            moduleIndex(sampleTree(), root, resolver)
                .resolve(ModulePath.of(":app:android")).directory shouldBe "apps/android"
        }
    }

    "モジュールの列挙" - {
        "build ファイルを持つディレクトリだけがモジュールになる" {
            discoverModules(sampleTree(), root).map { it.value } shouldContainExactly listOf(
                ":",
                ":app:android",
                ":core:data",
                ":core:data:remoteApi",
                ":feature:debug-menu",
                ":feature:home",
                ":feature:settings",
            )
        }

        "build ファイルの無いディレクトリはモジュールにならないが、その下は見に行く" {
            val modules = discoverModules(sampleTree(), root).map { it.value }
            modules.contains(":app") shouldBe false
            modules.contains(":app:android") shouldBe true
        }

        "Gradle が知らないディレクトリはモジュールにならない" {
            discoverModules(sampleTree(), root).map { it.value }.contains(":app:ios") shouldBe false
        }

        "included build のモジュールは列挙しない" {
            val modules = discoverModules(sampleTree(), root).map { it.value }
            modules.contains(":buildLogic") shouldBe false
            modules.contains(":buildLogic:convention") shouldBe false
        }

        "build.gradle でも build.gradle.kts でもモジュールとして扱う" {
            discoverModules(sampleTree(), root).map { it.value }.contains(":feature:settings") shouldBe true
        }

        "src の下は掘らない" {
            val fileSystem = fakeFileSystem(workingDirectory = "/repo") {
                "/repo" {
                    "build.gradle.kts"()
                    "app" {
                        "build.gradle.kts"()
                        "src" { "main" { "build.gradle.kts"() } }
                    }
                }
            }

            discoverModules(fileSystem, FsPath.of("/repo")).map { it.value } shouldContainExactly
                listOf(":", ":app")
        }
    }

    "ワイルドカードキーの展開" - {
        "\":feature:*\" はマッチしたモジュールの数だけ展開される" {
            expand(":feature:*").map { it.path.value } shouldContainExactly listOf(
                ":feature:debug-menu",
                ":feature:home",
                ":feature:settings",
            )
        }

        "展開ごとに別の捕捉値が付く" {
            expand(":feature:*").map { it.wildcards } shouldContainExactly listOf(
                listOf("debug-menu"),
                listOf("home"),
                listOf("settings"),
            )
        }

        "展開されたモジュールはそれぞれのディレクトリを持つ" {
            expand(":feature:*").map { it.directory } shouldContainExactly listOf(
                "feature/debug-menu",
                "feature/home",
                "feature/settings",
            )
        }

        "\":core:**\" は途中の階層とネストモジュールを両方拾う" {
            expand(":core:**").map { it.path.value to it.wildcards } shouldContainExactly listOf(
                ":core:data" to listOf("data"),
                ":core:data:remoteApi" to listOf("data", "remoteApi"),
            )
        }

        "マッチが0件でも展開結果が空になるだけで、必須のモジュールは生まれない" {
            expand(":nothing:*").map { it.path.value } shouldContainExactly emptyList()
        }
    }

    "ワイルドカードを含まないキーの展開" - {
        "名指ししたモジュールが1つだけ返る" {
            expand(":core:data").map { it.path.value to it.directory } shouldContainExactly
                listOf(":core:data" to "core/data")
        }

        "存在しないモジュールも解決先を持ったまま返る" {
            // The declaration below it then reports its build file as Missing, which says
            // more than dropping the whole module would.
            expand(":core:domain").map { it.path.value to it.directory } shouldContainExactly
                listOf(":core:domain" to "core/domain")
        }

        "捕捉値は空になる" {
            expand(":core:data").single().wildcards shouldContainExactly emptyList()
        }

        "\":\" はプロジェクトルートに展開される" {
            expand(":").map { it.path to it.directory } shouldContainExactly
                listOf(ModulePath.ROOT to "")
        }
    }
})
