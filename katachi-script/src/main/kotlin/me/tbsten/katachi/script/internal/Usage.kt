package me.tbsten.katachi.script.internal

// The --help text: the install kit's usage, rewritten for the katachi command. Hand-written prose,
// not generated from COMMAND_SPECS (a test checks that it names every option there).
// `katachi --help` lists what people type; `katachi agent --help` lists the steps of the install guide (KATACHI-290).

internal fun ScriptContext.usage(): String = pick(Messages.Usage.top)

/** `katachi agent --help`: the [CommandSpec.agent] commands. */
internal fun ScriptContext.agentUsage(): String = pick(Messages.Usage.agent)

/*
 * Environment variables for developers, kept out of --help (CLAUDE.md: what is for development is not shown in
 * the product). The prerelease skill's scripts set them; people installing katachi never do.
 *
 * KATACHI_MAVEN_LOCAL=1
 *     A hook for trying an unreleased katachi published to ~/.m2. Set it on agent init and it is recorded in the
 *     working directory; later scaffold runs follow it. Skips the "katachi needs 0.3.0 or later" check (not for a
 *     version fetched as the latest release from GitHub), and scaffold adds mavenLocal() to the repositories of
 *     pluginManagement and dependencyResolutionManagement in settings. Publish to ~/.m2 from the katachi repository
 *     with ./gradlew publishToMavenLocal -Pkatachi.skipSigning
 *
 * KATACHI_DOCS=<URL>
 *     Where agent docs fetches the guide from (default: https://tbsten.github.io/katachi). The guide and the
 *     templates ship with the command and do not use it. Point it at a built site (docs/dist, served) or a served
 *     URL; docs/public has no llms-full.txt and the like. Set it on agent init and it is recorded in the working
 *     directory; later commands such as agent docs follow it (running agent init again without it goes back to the
 *     default). agent docs prints the source URL to standard error.
 */
