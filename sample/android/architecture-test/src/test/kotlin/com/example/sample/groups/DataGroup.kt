package com.example.sample.groups

import com.example.sample.roles.repository
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the data layer: what `:data` holds. */
fun DeclarationContainerScope.dataGroup() = "data".group {
    title = "データレイヤー"
    summary = ":data が持つもの。データの取得と保存"
    description = """
        `:data` モジュール。いまは Repository 役割1つだけで、扱う対象ごとの package
        （`user` / `settings`）にインターフェースと実装を並べる。package は `DataDomain` の
        1項目ずつで、対象を増やすときはそこに1行足す。

        他のどのモジュールにも依存しない、このアプリで一番下の層。Android にも Compose にも
        触らないので、`:ui` や `:feature:*` を持ち出さずに読める。ViewModel はここの
        インターフェースをコンストラクタで受け取り、テストでは `:testing` のフェイクに差し替える。

        役割が1つしか無くても group にしてあるのは、増える場所だから。データソースや DTO を
        分けたくなったらここに足す。いまは Repository 以外のファイルを `:data` に置くと違反になるので、
        「置いてから考える」ができないようになっている。
    """.trimIndent()

    repository()
}

/**
 * What `:data` keeps a package for, by name: `User` is the `user` package.
 *
 * Read twice, so the list is written once. The Repository role's `layout { }` declares one
 * package per entry, and requires every file in it to start with that entry's name
 * (`user/User*Repository.kt`); each entry's own id (`--arg template=data.Repository.user`) is
 * what the Repository template takes, and Fake reads the same name back from `repository`.
 *
 * A fixed list rather than a directory `capture(...)` is what lets `layout { }` require that:
 * unlike a module key, whose `wildcard(...)` a layout can read back while it is still being
 * declared, a directory capture's value is not something the layout itself can read -- there is
 * no equivalent for a `capture(...)` level, only for a module's own `*`. So "every file below
 * `user/` starts with `User`" cannot be written as one pattern parameterised by a capture; each
 * domain's own literal package and prefix has to be written out. Choosing where a generated file
 * goes, rather than constraining its name, is the one thing a plain `capture(...)` would still do.
 */
enum class DataDomain {
    User,
    Settings,
    ;

    /** The package below `com.example.sample.data`, `user`. */
    val packageName: String get() = name.lowercase()
}
