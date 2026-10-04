# Module katachi-konsist

Adds `konsist { }` to the katachi DSL: a constraint written with Konsist, evaluated as one more
constraint kind alongside katachi's own. Also adds `ktFileContent { }`, an allow list of what a
role's Kotlin files may hold.

# Package me.tbsten.katachi.konsist

The `konsist { }` backend: declares a named constraint that runs a Konsist scope over the files
a role's `layout { }` covers.

# Package me.tbsten.katachi.konsist.declarations

`ktFileContent { }`: what a role's Kotlin files may hold -- their package, their imports, the
declarations directly in them and the members of their classes -- written in the order and shape
of a Kotlin file, as an allow list. Only the kinds a line is written for are checked. Checked by
`FileConstraintCheck()`, like `konsist { }`, and experimental (`@ExperimentalKatachiApi`) while
katachi is at v0.3.
