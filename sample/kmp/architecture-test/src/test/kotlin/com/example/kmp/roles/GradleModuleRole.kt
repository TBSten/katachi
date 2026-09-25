package com.example.kmp.roles

import com.example.kmp.processor.owner
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The `build.gradle.kts` every module of this sample carries. */
fun DeclarationContainerScope.gradleModule() = "GradleModule" {
    title = "モジュールのビルドスクリプト"
    // Covers `architecture-test/build.gradle.kts` as well: that module is a module
    // like any other, and the price of giving katachi a home of its own is that its
    // build script shows up here.
    summary = "各モジュールの build.gradle.kts"
    documented = false
    owner = "platform"
    description = """
        各モジュールが1つずつ持つ `build.gradle.kts` です。layout は
        `settings.gradle.kts` と同じ並びで、1モジュール1行書いています。

        空の `.module { }` は空の宣言ではありません。module ブロックは常に
        「このモジュールの `build/` は検査しない」と「`build.gradle.kts` が無ければならない」の
        2つを言うので、中身が空でもその2つが宣言されます。そのモジュールのソースがどこに行くかは、
        それぞれの役割が別に言います。

        ワイルドカードにしてあるのは `:feature:*` だけです。feature はモジュールが増える前提の
        場所なので、増えてもこの一覧を触らずに済みます。裏を返すと、`feature/` の下にモジュール
        ではないディレクトリを作ると、どの役割も名乗らないので `[UnexpectedFile]` になります。

        `:architecture-test` もここに出てきます。katachi に専用モジュールを与えた代償で、
        あのモジュールも他と同じく1つのモジュールだからです。

        `documented = false` を group と役割の両方に書いているのは、katachi が宣言された値を
        そのまま保ち、親から継承しないからです。読む側が `group[Documented] ?: true` と
        組み合わせます。`owner = "platform"` はこのサンプル独自のメタデータキーで、
        `PlatformOwnedFilesProcessor` だけが読みます。
    """.trimIndent()
    example("data/build.gradle.kts", ":data のビルドスクリプト")
    example("architecture-test/build.gradle.kts", ":architecture-test のビルドスクリプト")
    // One line per module, written the way `settings.gradle.kts` writes it. An empty
    // `.module { }` block is not an empty declaration: every module block says
    // `build/` is not checked and `build.gradle.kts` has to be there, which is the
    // whole of this role. The other roles say where that module's sources go.
    //
    // Only `:feature:*` is written with a wildcard, because that is the one place
    // where modules are expected to multiply. It stands for the feature modules that
    // exist, so a new one is picked up without this list being touched — and a
    // `feature/` directory that is not a module at all has nothing claiming it.
    layout {
        ":app:android".module { }
        ":architecture-test".module { }
        ":data".module { }
        ":feature:*".module { }
        ":navigation".module { }
        ":testing".module { }
        ":ui".module { }
    }
}
