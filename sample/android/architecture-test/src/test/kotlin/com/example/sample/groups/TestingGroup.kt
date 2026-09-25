package com.example.sample.groups

import com.example.sample.roles.architectureDefinition
import com.example.sample.roles.fake
import com.example.sample.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles that exist for testing: the shared fakes in `:testing`, the tests themselves, and
 * the architecture definition.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own
 * rather than hiding inside `Test`: `ArchitectureDefinition` describes the shape, `Test`
 * asserts behaviour. The two share one module and are told apart by where the file sits:
 * the definition is `ProjectArchitecture.kt` plus the `Group.kt` and `Role.kt` files of the
 * `groups` and `roles` packages, and everything `*Spec.kt` or `*Test.kt` at the top of the
 * package is a test.
 *
 * `:architecture-test` is also the second module whose package does not follow its module
 * path — it would come out as `com/example/sample/architectureTest` — so the two roles
 * write `com/example/sample` out as a key. `:testing` does follow it and uses
 * `modulePackage`, which is what makes the difference visible side by side.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト"
    summary = "アプリを確かめるためにあるもの。共有のフェイク、テスト、アーキテクチャ定義"
    description = """
        アプリのレイヤーではなく、アプリを確かめるためにあるもの。3つ集めてある。
        フェイクは `:testing` にある差し替え用の実装、テストコードはテストそのもの、
        アーキテクチャ定義は katachi の DSL で書かれたこの定義自身。

        アーキテクチャ定義をテストコードに混ぜていないのは、2つが別のことを書いているから。
        定義は「どんな形をしているか」、テストは「どう振る舞うか」。どちらも
        `:architecture-test` にあり、ファイルの場所と名前で区別される
        （`ProjectArchitecture.kt` と `groups/` `roles/` が定義、package 直下の
        `*Spec.kt` `*Test.kt` がテスト）。定義を書き換える人とテストを足す人は
        だいたい別のことをしているので、ドキュメント上でも別項目にしてある。

        `:testing` だけがアプリ側のモジュールで、`:data` のインターフェースを満たす `Fake*` を
        `main` ソースセットに置いて他モジュールのテストへ公開する。テスト用のコードを
        あえて製品コードとして出すのは、`src/test` が他モジュールから見えないため。

        `:architecture-test` は `:app` と並ぶ、package がモジュールパスから導けないモジュール。
        そのまま当てると `com/example/sample/architectureTest` になるので、この group の
        2つの役割はどちらも `com/example/sample` を直接書いている。
    """.trimIndent()

    fake()
    test()
    architectureDefinition()
}
