# Add a rule the team wants enforced

Placement says where a file is. Many conventions are about what is inside it: "a ViewModel does not import `android.*`", "a repository implementation is `internal`", "every feature module's namespace matches its package". These are constraints on contents.

Read the documentation before writing one: https://tbsten.github.io/katachi/guides/kt-file-content/ . For signatures, the API reference: https://tbsten.github.io/katachi/api-docs/katachi-konsist/llms.txt .

## Where the rule goes

| The rule is about | Write it as |
|---|---|
| What a Kotlin file of one Role may declare or import | `ktFileContent { }` in a function the Role calls (from `katachi-konsist`) |
| A build file (`build.gradle.kts`, settings, the version catalog) | `fileConstraint("…") { subject -> … }` in a `gradle { }` hook: `buildScript { }`, `settingsScript { }`, `versionCatalog { }` |
| Any other file's contents | `fileConstraint("…") { subject -> … }` in a function the Role calls (`private fun RoleScope.xNoHttp() = fileConstraint(…) { … }`, called from `"X".role { xNoHttp() }`) |

`ktFileContent { }`, `fileConstraint` and `FileConstraintFailure` are experimental: a file that uses one starts with `@file:OptIn(ExperimentalKatachiApi::class)` and `import me.tbsten.katachi.ExperimentalKatachiApi`. Two of them also need an import of their own: `import me.tbsten.katachi.konsist.declarations.ktFileContent` for `ktFileContent { }`, and `import me.tbsten.katachi.dsl.FileConstraintFailure` for the failures a `fileConstraint` returns (`fileConstraint` itself needs none). Pass the reasons, allowed entries and fixes to it as plain values: `FileConstraintFailure(file, reasons = listOf("Missing" to text), howToFix = listOf("sentence"))`. Read a covered file with `subject.textOf(file)`. When the check looks at one file at a time and most files are fine, write it as `buildList { for (file in subject.files) { … continue … add(FileConstraintFailure(…)) } }`: `continue` skips a file that is fine, so the lambda never returns `null` for it.

`ktFileContent { }` is allow-list based: the imports and declarations you list are what is allowed, and anything else is a violation. "Must not import X" is written by listing what may be imported and leaving X out.

Write a constraint as a function the Role calls, not inside the Role's braces (`ktFileContent`, `fileConstraint`, `konsist` and `konture` alike, however short). Put it under the Role in the same file, `private`, and not `inline` (a violation's declaration site is read off the stack trace). It is named `<role>KtFileContent()` for a `ktFileContent { }` and `<role><Rule>Constraint()` for the others. A constraint in a `gradle { }` hook is not inside a Role, so it stays there.

```kt
fun DeclarationContainerScope.viewModel() = "ViewModel".role {
  layout { /* … */ }

  viewModelKtFileContent()
}

private fun RoleScope.viewModelKtFileContent() = ktFileContent {
  `package`("com.example.viewModel")

  // android.** is not listed, so importing it is a violation
  `import`("kotlin.**")
  `import`("kotlinx.coroutines.**")
  `import`("androidx.lifecycle.**")
  `import`("com.example.**")

  required(`class`, fileName) {
    allow(anythingElse)
  }
}
```

A `buildScript { }` constraint receives the build files of every module in `subject.files`; to look at one module, filter them by path inside the constraint (`subject.files.filter { it == "app/build.gradle.kts" }`).

Name the constraint after the rule (`"no android imports in ViewModel".ktFileContent { … }`), so a violation says which rule it broke.

## Do not skip a rule because another tool has it

If detekt, ktlint, Android Lint or a compiler plugin checks something similar, the rule still belongs in katachi when it is part of what a Role is ("a ViewModel does not call a repository directly" defines the ViewModel Role). Leave it out only when the other tool already fails the build and cannot be suppressed per file.

## Then

Run the check. A new rule often finds existing code that breaks it. Do not loosen the rule to get to 0 violations, and do not rewrite the code without asking: list the violations for the user, with what each would take to fix. If they want to fix them later, the baseline can hold the current ones ([../fix-violations/README.md](../fix-violations/README.md), "Tolerating what is there now").

Then make sure the rule really fires: with a green result, write one temporary file that breaks it, see the violation, and delete the file.
