package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.DataDomain
import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of a stand-in implementation other modules' tests use.
 *
 * `:testing` follows the module path convention, so its package comes from `modulePackage`
 * rather than being written out the way `:architecture-test` has to write its own.
 */
fun DeclarationContainerScope.fake() = "Fake" {
    title = "フェイク"
    summary = ":testing に置く、他モジュールのテストから使う偽の実装"
    description = """
        `:data` のインターフェースを、メモリ上の値だけで満たすテスト用の実装。
        `FakeUserRepository` は `UserRepository` を、`FakeSettingsRepository` は
        `SettingsRepository` を実装する。コンストラクタ引数に既定値を持たせてあるので、
        テスト側はその回に関係のある値だけを書けばよい。

        `src/test` ではなく `main` に置いてあるのがこの役割の肝。`src/test` のコードは
        同じモジュールからしか見えないので、他モジュールのテストへ渡すには
        製品コードとして公開するしかない。利用側は `testImplementation(project(":testing"))` で
        取り込む。`:testing` が `:data` に `api` で依存しているのも、取り込んだ側が
        インターフェースごと受け取れるようにするため。

        ファイル名は `Fake*.kt`。`:testing` に置けるのは差し替え用の実装だけで、
        テストのヘルパーやカスタムアサーションを足したくなったら、まず役割を増やす。
        テストそのものは別の役割（テストコード）で、`src/test` にある。
    """.trimIndent()
    forbiddenContents = """
        本番から呼ばれるコード。`:testing` に依存してよいのは
        テストのコンパイル経路だけで、`:app` や `:feature:*` の `main` からは参照しない。
    """.trimIndent()
    example("FakeUserRepository", "UserRepository のメモリ実装")
    example("FakeSettingsRepository", "SettingsRepository のメモリ実装")
    layout {
        ":testing".module {
            mainSourceSet / kotlin / modulePackage / "Fake*".ktFile()
        }
    }
    // Implements what the Repository template generates for the same `domain` and `name`,
    // so run that one first: the fake of an interface that is not there does not compile.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg roleName=Fake --arg domain=User --arg name=Profile
    template {
        val domain by enumParameter(DataDomain.entries)
        val name by stringParameter()
        val repository = "${domain.name}${name}Repository"

        file("Fake$repository.kt") {
            """
                package com.example.sample.testing

                import com.example.sample.data.${domain.packageName}.$repository

                /** In-memory [$repository] for tests of other modules. */
                class Fake$repository(
                    private var value: String = "",
                ) : $repository {
                    override fun load(): String = value
                }
            """.trimIndent()
        }
    }
}
