package me.tbsten.katachi.script.internal

// The --help text: the install kit's usage, rewritten for the katachi command. Hand-written prose,
// not generated from COMMAND_SPECS (a test checks that it names every option there).

internal fun ScriptContext.usage(): String = pick(Messages.Usage.top)

/*
 * Environment variables for developers, kept out of --help (CLAUDE.md: what is for development is not shown in
 * the product). The prerelease skill's scripts set them; people installing katachi never do.
 *
 * KATACHI_MAVEN_LOCAL=1
 *     A hook for trying an unreleased katachi published to ~/.m2. Set it on scaffold. Skips the "katachi needs 0.3.0 or later" check (not for a
 *     version fetched as the latest release from GitHub), and scaffold adds mavenLocal() to the repositories of
 *     pluginManagement and dependencyResolutionManagement in settings. Publish to ~/.m2 from the katachi repository
 *     with ./gradlew publishToMavenLocal -Pkatachi.skipSigning
 */
