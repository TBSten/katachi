# Module katachi-script

The logic behind the `katachi` command. `katachi.main.kts` pulls this artifact in with
`@file:DependsOn` and calls `main`. Every subcommand is written here; the install guide and the
templates `init` places ship inside the artifact.

# Package me.tbsten.katachi.script

The entry point `main(args)`, and where a script starts building a `katachi` command: `Katachi`,
one entry point per subcommand (`katachi.add`, `katachi.scaffold`, ...) and the values their
options take. The subcommands themselves, their option specs, the working directory lookup and
the edits to the user's Gradle files are internal.

# Package me.tbsten.katachi.script.command

The builders the entry points return, one per subcommand (`ScaffoldCommand`, `AddRoleCommand`,
...). A script rarely names them: it chains their methods from `katachi.<subcommand>` and calls
`execute()`.
