# Module katachi-script

The logic behind the `katachi` command. `katachi.main.kts` pulls this artifact in with
`@file:DependsOn` and calls `main`; subcommands not yet written in Kotlin are handed to the
install kit's `katachi-install.sh`.

# Package me.tbsten.katachi.script

The entry point `main(args)`. Everything else is internal: the subcommands, the working
directory lookup, and the hand-off to the shell script.
