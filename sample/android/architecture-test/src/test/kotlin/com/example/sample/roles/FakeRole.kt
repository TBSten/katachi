package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.DataDomain
import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

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

        テンプレートから生成できる。`repository` に渡すのは実装したいインターフェースの名前
        そのもの（`UserRepository`）で、`--arg template=testing.Fake --arg repository=UserRepository`
        で `FakeUserRepository.kt` ができる。どの領域の package に置くかは、名前の頭が
        `DataDomain` のどれと一致するかで決める。
    """.trimIndent()
    forbiddenContents = """
        本番から呼ばれるコード。`:testing` に依存してよいのは
        テストのコンパイル経路だけで、`:app` や `:feature:*` の `main` からは参照しない。
    """.trimIndent()
    example("FakeUserRepository", "UserRepository のメモリ実装")
    example("FakeSettingsRepository", "SettingsRepository のメモリ実装")
    // Implements what the Repository template generates (`--arg name=` there is folded into
    // `repository` here), so run that one first: the fake of an interface that is not there
    // does not compile.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=testing.Fake --arg repository=UserProfileRepository
    layout {
        ":testing".module {
            mainSourceSet / kotlin / modulePackage / "Fake${capture("repository")}".ktFile()
                .template {
                    val repository = captureValue("repository")
                    // The package a repository interface lives in is not part of its own name, so it
                    // is found the same way DataDomain.packageName itself is used elsewhere: by the
                    // domain name it starts with.
                    val domain = DataDomain.entries.firstOrNull { repository.startsWith(it.name) }
                    require(isPreview || domain != null) {
                        "--arg repository=$repository: must start with one of " +
                            DataDomain.entries.joinToString { it.name }
                    }
                    val packageName = "com.example.sample.data.${(domain ?: DataDomain.entries.first()).packageName}"

                    """
                        package com.example.sample.testing

                        import $packageName.$repository

                        /** In-memory [$repository] for tests of other modules. */
                        class Fake$repository(
                            private var value: String = "",
                        ) : $repository {
                            override fun load(): String = value
                        }
                    """.trimIndent() + "\n"
                }
        }
    }
}
