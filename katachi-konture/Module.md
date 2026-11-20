# Module katachi-konture

Adds `konture { }` to the katachi DSL: a constraint written with Konture's `files { } / classes { } /
functions { } / properties { }` rules, evaluated as one more constraint kind alongside katachi's own.

Needs Kotlin 2.3 or later, one step above katachi's own floor of 2.2: Konture 1.0.1, which this module
brings in as an `api` dependency, is compiled with Kotlin 2.4 metadata, and a Kotlin 2.2 compiler reads
metadata up to 2.3 only. On 2.2 the test module fails to compile with "Module was compiled with an
incompatible version of Kotlin" naming the Konture jar.

# Package me.tbsten.katachi.konture

The `konture { }` backend: declares a named constraint that runs Konture's rules over the files
a role's `layout { }` covers, without Konture's Gradle plugin or `layout_v2.json`. A block written
without a name is named `konture`, and a role holds one constraint of each name.
