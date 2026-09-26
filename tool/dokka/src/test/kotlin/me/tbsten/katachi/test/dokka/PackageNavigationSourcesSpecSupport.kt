package me.tbsten.katachi.test.dokka

/** Inline sources whose packages exercise the nesting of the sidebar. */
internal object PackageNavigationSources {
    /**
     * `com.example.data` with two sub-packages, `com.example.domain.model` (whose `domain` has no
     * package of its own) and `com.example.feature.foo.bar` (a chain of such segments), with one
     * featured declaration.
     */
    val APP: String = """
        |/src/main/kotlin/com/example/data/Repository.kt
        |package com.example.data
        |
        |/**
        | * Reads and writes the data.
        | *
        | * @featured
        | */
        |public interface Repository
        |
        |/** Makes the default repository. */
        |public fun defaultRepository(): Repository = object : Repository {}
        |
        |/src/main/kotlin/com/example/data/local/LocalSource.kt
        |package com.example.data.local
        |
        |/** Reads from the disk. */
        |public class LocalSource
        |
        |/src/main/kotlin/com/example/data/remote/RemoteSource.kt
        |package com.example.data.remote
        |
        |/** Reads from the network. */
        |public class RemoteSource
        |
        |/src/main/kotlin/com/example/domain/model/User.kt
        |package com.example.domain.model
        |
        |/** A user. */
        |public class User
        |
        |/src/main/kotlin/com/example/feature/foo/bar/BarScreen.kt
        |package com.example.feature.foo.bar
        |
        |/** The bar screen. */
        |public class BarScreen
    """.trimMargin()

    /** `com.example.core`, a real package with a single sub-package `util`. */
    val CORE: String = """
        |/src/main/kotlin/com/example/core/Core.kt
        |package com.example.core
        |
        |/**
        | * The core.
        | *
        | * @featured
        | */
        |public class Core
        |
        |/src/main/kotlin/com/example/core/util/Strings.kt
        |package com.example.core.util
        |
        |/** Trims a string. */
        |public fun trimmed(value: String): String = value.trim()
    """.trimMargin()
}
