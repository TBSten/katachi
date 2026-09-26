# Module katachi

The DSL to declare a project's architecture, the check that walks a real project against that
definition, and the processor that turns the same definition into generated documentation or
generated code.

# Package me.tbsten.katachi

Markers and the exception bases every other package builds on: the two opt-in annotations, and
the three roots of katachi's exception hierarchy.

# Package me.tbsten.katachi.check

The entry points that turn an [me.tbsten.katachi.dsl.Architecture] and a project into a pass or
a failure — `assert`, `validate`, the `layout { }` check and the check that evaluates
`constraint { }` blocks — and what they find while they walk a project: a violation, its kind
and severity, and the warnings a definition can carry on its own.

# Package me.tbsten.katachi.docs

Writes the role reference of a definition to disk, as Markdown.

# Package me.tbsten.katachi.dsl

The `architecture { }` DSL itself: groups, roles, `layout { }`, constraints and the metadata a
definition carries. It also holds `FileSelection` — which files a check walks, chosen with
`files = gitTracked()` or `files = wholeTree()` — and the exceptions raised when that choice
cannot be applied.

# Package me.tbsten.katachi.dsl.files

The small file system abstraction a check walks a project through, real or in-memory: the
extension point a custom `FileSelection` is written against, together with the path and project
root types it speaks in.

# Package me.tbsten.katachi.dsl.gradle

DSL vocabulary for a Gradle multi-module project: module discovery, source sets, and the
`"Gradle"` group every build has.

# Package me.tbsten.katachi.dsl.kotlin

File name helpers written on top of the core DSL, such as `ktFile()`, as a worked example of the
kind of utility layer a project writes for itself.

# Package me.tbsten.katachi.processor

The extension point a definition is read through: something that takes an
[me.tbsten.katachi.processor.ArchitectureProcessContext] and answers with a `Result`, the way
both the check and documentation generation do.

# Package me.tbsten.katachi.template

Writes the files a role's `template { }` produces into the project.

# Package me.tbsten.katachi.util

Small utilities shared across katachi's own modules, such as `runCatchingScoped`.
