package com.example.kmp.groups

import com.example.kmp.roles.gradleModule
import com.example.kmp.roles.gradleRoot
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Build scripts. Not documented: they are part of the repository's shape but not part of the
 * architecture a reader of the docs needs.
 *
 * `documented = false` is written on the group and on each role, because katachi keeps the
 * declared value as written and does not inherit it from the parent.
 *
 * Both roles also carry `owner = "platform"`, this sample's own metadata key (see
 * [com.example.kmp.processor.Owner]). It is not part of katachi -- it exists only to give
 * `com.example.kmp.processor.PlatformOwnedFilesProcessor` something to read, and to demonstrate
 * that a user of katachi can bring their own vocabulary the same way `documented` and `summary`
 * do. `tool/Git` carries it too, which is why the key is documented where it is declared rather
 * than here.
 *
 * No role for `build/`, `.kotlin/` or `local.properties`. They exist on a developer machine and
 * on CI but are in none of the commits, and `files` is left at its default `gitTracked()`, so
 * git is the one deciding which files this project has -- and it never reports an ignored file.
 * Nothing has to be declared to keep them out.
 */
fun DeclarationContainerScope.buildGroup() = "build".group {
    documented = false
    title = "ビルド"
    summary = "各モジュールの build.gradle.kts と、その周りに置くビルドファイル"
    description = """
        ビルドスクリプトです。2つの役割の分かれ目はモジュールの中か外かで、GradleModule が
        各モジュールの `build.gradle.kts`、GradleRoot が `settings.gradle.kts` や wrapper など
        モジュールの周りにあるものです。

        `documented = false` を書いています。リポジトリの形の一部ではありますが、生成される
        ドキュメントを読む人が探しているアーキテクチャではないからです。検査からは外れません。
        group と各役割の両方に書いているのは、katachi が宣言された値をそのまま保ち、
        親から継承しないためです。省略は「書かなかった」として残り、それを true と読むのは
        読む側の仕事になります。

        `build/` や `.kotlin/`、`local.properties` のための役割はありません。開発者のマシンと
        CI には存在しますが、どのコミットにも入っていないからです。`files` は既定の
        `gitTracked()` のままなので、このプロジェクトがどのファイルを持つかを決めるのは git で、
        git は無視したファイルを報告しません。締め出すために何かを宣言する必要はありません。

        2つの役割はどちらも `owner = "platform"` を持ちます。これは katachi の語彙ではなく
        このサンプルが自分で足したメタデータキーで、`PlatformOwnedFilesProcessor` だけが
        読みます。同じキーを `tool/Git` も持っています。
    """.trimIndent()

    gradleModule()
    gradleRoot()
}
