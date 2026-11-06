# Module katachi-script

The logic behind the `katachi` command. `katachi.main.kts` pulls this artifact in with
`@file:DependsOn` and calls `main`. Every subcommand is written here; the install guide and the
templates `init` places ship inside the artifact.

# Package me.tbsten.katachi.script

The entry point `main(args)`. Everything else is internal: the subcommands, their option
specs, the working directory lookup, and the edits to the user's Gradle files.
