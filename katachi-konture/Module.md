# Module katachi-konture

Adds `konture { }` to the katachi DSL: a constraint written with Konture's `files { } / classes { } /
functions { } / properties { }` rules, evaluated as one more constraint kind alongside katachi's own.

# Package me.tbsten.katachi.konture

The `konture { }` backend: declares a named constraint that runs Konture's rules over the files
a role's `layout { }` covers, without Konture's Gradle plugin or `layout_v2.json`.
