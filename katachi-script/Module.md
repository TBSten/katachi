# Module katachi-script

The logic behind the `katachi` command. `katachi.main.kts` pulls this artifact in with
`@file:DependsOn` and calls `main`. Every subcommand is written here: `scaffold`, `lint`, `doctor`
and the `katachi agent` ones the katachi skill runs.

# Package me.tbsten.katachi.script

The entry point `main(args)`, and what a script types to build a `katachi` command: `Katachi`,
one entry point per subcommand (`katachi.scaffold`, `katachi.lint`, `katachi.agent.test`, ...),
`commands` / `executeCommands`, and the bare names of option values (`kotest`, `ja`, ...). The
subcommands themselves, their option specs, the working directory lookup and the edits to the
user's Gradle files are internal.

# Package me.tbsten.katachi.script.command

The commands the entry points return: one builder per subcommand (`ScaffoldCommand`,
`LintCommand`, ...), the `AgentCommandGroup` that holds the `katachi agent` ones, the
`KatachiCommand` they all are, and the `CommandRunner` that runs them. A script rarely names the
builders: it chains their methods from `katachi.<subcommand>` and calls `execute()`.

# Package me.tbsten.katachi.script.option

The values an option takes when it has a fixed set of them (`Lang`, `TestFramework`, ...). Most
have bare names in `me.tbsten.katachi.script`, so a script writes `kotest` rather than
`TestFramework.Kotest`.

# Package me.tbsten.katachi.script.error

What a command throws instead of running, such as a required option left out.
