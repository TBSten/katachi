package com.example.kmp.roles

import com.example.kmp.processor.owner
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * What git needs to be told about this project.
 *
 * No `title` here on purpose: an undocumented role has no display name to show, so this is the
 * one place in this sample that exercises the default — the role name itself.
 * `ProjectArchitectureSpec` asserts it.
 */
fun DeclarationContainerScope.git() = "Git" {
    summary = ".gitignore。ビルド生成物と Xcode の作業ファイルを Git の管理から外す"
    documented = false
    // Also `owner = "platform"` (see com.example.kmp.processor.Owner), this sample's own
    // metadata key -- not katachi's. `PlatformOwnedFilesSpec` builds a variant of
    // this exact role with the tag left out to prove its processor really reads it.
    owner = "platform"
    description = """
        git に伝えることだけを持つ役割です。今あるのは `.gitignore` 1ファイルで、
        `build/` `.gradle/` `.kotlin/` `local.properties`、それに Xcode の `xcuserdata/` と
        `DerivedData/` を Git の管理から外しています。

        この役割はこのサンプルの検査そのものを支えています。`files` を既定の `gitTracked()` の
        ままにしてあるので、katachi に渡るファイルは git が追跡しているものだけです。つまり
        `build/` のための役割を書く必要がなく、逆に `.gitignore` を緩めると、その瞬間から
        生成物が「どの役割も名乗らないファイル」として報告され始めます。

        このサンプルで唯一 `title` を書いていない宣言でもあります。表示名を省いたときに何が
        出るか（役割名がそのまま使われること）を実際に試す場所として残してあり、
        `ProjectArchitectureSpec` がそれを固定しています。

        置いてはいけないもの:

        - ビルドファイル。`build.gradle.kts` や wrapper は build group の役割です
        - CI の設定。このサンプルは自前の `.github/` を持たず、リポジトリルートの
          ワークフローから回されています
    """.trimIndent()
    example(".gitignore", "Git が無視するものの一覧")
    layout {
        ".gitignore".file()
    }
}
