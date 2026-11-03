package me.tbsten.katachi.dsl

/**
 * Receiver of `gradle { }`: a [GroupScope] with one hook per Gradle role, for writing constraints and metadata on it.
 *
 * Everything a group block takes is still written here — `title`, `summary`, `documented`,
 * `owner = "..."` and the project's own `"X".role { }` / `"X".group { }` — and the hooks come on
 * top. A hook names a role, and its block is a [GradleRoleScope]: a constraint or a metadata
 * value for that role, and nothing about where its files live, because the layout is
 * katachi's. The same hook may be written more than once; each block is applied in the order
 * written, so the constraints line up the way they were written, as several `layout { }`
 * blocks do.
 *
 * | hook | role | files the constraint receives |
 * |---|---|---|
 * | [launcherScript] | `Gradle.GradleWrapper.LauncherScript` | `gradlew`, `gradlew.bat` |
 * | [wrapperJar] | `Gradle.GradleWrapper.WrapperJar` | `gradle/wrapper/gradle-wrapper.jar` |
 * | [wrapperProperties] | `Gradle.GradleWrapper.WrapperProperties` | `gradle/wrapper/gradle-wrapper.properties` |
 * | [settingsScript] | `Gradle.SettingsScript` | `settings.gradle.kts` or `settings.gradle` |
 * | [buildScript] | `Gradle.BuildScript` | `build.gradle.kts` or `build.gradle` of every module, the root project included |
 * | [gradleProperties] | `Gradle.GradleProperties` | `gradle.properties` |
 * | [versionCatalog] | `Gradle.VersionCatalog` | `*.versions.toml` directly inside `gradle` |
 * | [daemonJvmProperties] | `Gradle.DaemonJvmProperties` | `gradle/gradle-daemon-jvm.properties` |
 *
 * ## Example 1: a constraint on the build scripts, and a role of the project's own
 * ```kt
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.gradle.gradle
 *
 * val arch = architecture {
 *     gradle {
 *         buildScript {
 *             fileConstraint("fails the build on every compiler warning") { subject -> emptyList() }
 *         }
 *         versionCatalog {
 *             fileConstraint("no snapshot versions") { subject -> emptyList() }
 *         }
 *         "DependencyVerification".role {
 *             documented = false
 *             layout { "gradle/verification-metadata.xml".file() }
 *         }
 *     }
 * }
 * ```
 *
 * @see me.tbsten.katachi.dsl.gradle.gradle
 * @see GradleRoleScope
 */
@KatachiDsl
public sealed interface GradleScope : GroupScope {
    /**
     * Opens `Gradle.GradleWrapper.LauncherScript`, the role of `gradlew` and `gradlew.bat`.
     */
    public fun launcherScript(block: GradleRoleScope.() -> Unit)

    /**
     * Opens `Gradle.GradleWrapper.WrapperJar`, the role of `gradle/wrapper/gradle-wrapper.jar`.
     */
    public fun wrapperJar(block: GradleRoleScope.() -> Unit)

    /**
     * Opens `Gradle.GradleWrapper.WrapperProperties`, the role of `gradle/wrapper/gradle-wrapper.properties`.
     */
    public fun wrapperProperties(block: GradleRoleScope.() -> Unit)

    /**
     * Opens `Gradle.SettingsScript`, the role of `settings.gradle.kts` or `settings.gradle`.
     */
    public fun settingsScript(block: GradleRoleScope.() -> Unit)

    /**
     * Opens `Gradle.BuildScript`, the role of every module's `build.gradle.kts` or `build.gradle`.
     */
    public fun buildScript(block: GradleRoleScope.() -> Unit)

    /**
     * Opens `Gradle.GradleProperties`, the role of `gradle.properties`.
     */
    public fun gradleProperties(block: GradleRoleScope.() -> Unit)

    /**
     * Opens `Gradle.VersionCatalog`, the role of every `*.versions.toml` directly inside `gradle`.
     */
    public fun versionCatalog(block: GradleRoleScope.() -> Unit)

    /**
     * Opens `Gradle.DaemonJvmProperties`, the role of `gradle/gradle-daemon-jvm.properties`.
     */
    public fun daemonJvmProperties(block: GradleRoleScope.() -> Unit)
}

/**
 * Receiver of a [GradleScope] hook such as `buildScript { }`: what may be written on a role
 * `gradle()` declares.
 *
 * It is a [FileConstraintScope], so `fileConstraint(name) { subject -> ... }` is written here
 * exactly as it is directly on a role of the project's own, and covers the union of every file
 * the role declares. The extensions a backend adds on `FileConstraintScope` — `ktFileContent { }`,
 * `"name".konsist { }`, `"name".konture { }` — resolve here as well. It is a [MetadataScope], so
 * a word a processor brings in, `var MetadataScope.owner by Owner`, lands on that one role.
 *
 * It is deliberately not a [RoleScope]: `layout { }` is left out because where a Gradle file
 * lives is katachi's decision, and `title` / `summary` / `documented` / `example()` are left out
 * because `gradle()` decides them and keeps the whole group out of the generated documentation.
 * A constraint with `scope = FileConstraintRange.DirectOnly` is refused as it is directly on a
 * role, with [KatachiFileConstraintDirectOnlyWithoutDirectoryException]: a hook has no
 * directory block of its own.
 *
 * ## Example 1: a constraint and a metadata value on the build scripts
 * ```kt
 * import me.tbsten.katachi.dsl.MetadataKey
 * import me.tbsten.katachi.dsl.MetadataScope
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.gradle.gradle
 * import me.tbsten.katachi.dsl.metadata
 *
 * val Owner: MetadataKey<String> = metadata()
 * var MetadataScope.owner: String? by Owner
 *
 * val arch = architecture {
 *     gradle {
 *         buildScript {
 *             owner = "platform"
 *             fileConstraint("fails the build on every compiler warning") { subject -> emptyList() }
 *         }
 *     }
 * }
 * ```
 *
 * @see GradleScope
 * @see me.tbsten.katachi.dsl.gradle.gradle
 */
@KatachiDsl
public sealed interface GradleRoleScope : MetadataScope, FileConstraintScope
