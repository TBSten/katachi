package com.example.kmp

import com.example.kmp.application.appRoles
import com.example.kmp.application.dataRoles
import com.example.kmp.application.featureRoles
import com.example.kmp.application.uiRoles
import com.example.kmp.gradle.gradleRoles
import com.example.kmp.testing.testingRoles
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import me.tbsten.katachi.dsl.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.process

/**
 * A processor written outside `:katachi`, run against this sample's own definition.
 *
 * [PlatformOwnedFilesProcessor] and the [Owner] key it reads are both declared in this sample,
 * not in katachi. That is the whole point of this file: a user can add a processor of their
 * own on top of the one katachi ships (`assert()` / [me.tbsten.katachi.check.LayoutCheck]),
 * using nothing but the published processor API.
 *
 * The processor API is `@ExperimentalKatachiApi`, so this file opts in. That opt-in is the
 * wall doing its job: a real consumer of the published `katachi` artifact writes exactly this
 * to depend on a shape that is still moving, which is different from writing `owner = "..."`
 * in [com.example.kmp.gradle.gradleRoles] -- that needs no opt-in, because the sugar hides it.
 */
@OptIn(ExperimentalKatachiApi::class)
class PlatformOwnedFilesSpec : FreeSpec({
    "owner = \"platform\" が付いた役割のファイルをすべて集める" {
        val files = projectArchitecture.process(PlatformOwnedFilesProcessor(owner = "platform"))

        files shouldContainExactlyInAnyOrder (platformOwnedFilesWithoutGit + ".gitignore")
    }

    "owner タグを外した役割からはファイルが集まらない（空振り防止）" {
        // tool/Git と同じ layout を持つが owner を書かない役割で組み直した定義。本物の
        // toolRoles() との違いはこの1タグだけなので、processor が実際には Owner を
        // 読まずに全ファイルを返しているだけなら、ここでも .gitignore が混ざる。
        val architectureWithUntaggedGit = architecture {
            featureRoles()
            uiRoles()
            dataRoles()
            testingRoles()
            appRoles()
            gradleRoles()
            "tool".group {
                documented = false
                title = "ツール"

                "Git" {
                    summary = ".gitignore など"
                    documented = false
                    example(".gitignore", "生成物を Git の管理から外す")
                    layout {
                        ".gitignore".file()
                    }
                    // owner is deliberately left unset.
                }
            }
        }

        val files = architectureWithUntaggedGit.process(PlatformOwnedFilesProcessor(owner = "platform"))

        files shouldContainExactlyInAnyOrder platformOwnedFilesWithoutGit
    }
})

/**
 * Every file `owner = "platform"` reaches through `build/GradleModule` and `build/GradleRoot`,
 * shared by both tests above so the file list is written once. `tool/Git`'s `.gitignore` is
 * deliberately not here: it is the one file that tells the two tests apart.
 */
private val platformOwnedFilesWithoutGit = listOf(
    "app/android/build.gradle.kts",
    "architecture-test/build.gradle.kts",
    "data/build.gradle.kts",
    "feature/home/build.gradle.kts",
    "feature/settings/build.gradle.kts",
    "navigation/build.gradle.kts",
    "testing/build.gradle.kts",
    "ui/build.gradle.kts",
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "gradle/sample.versions.toml",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
)
