## 6. Report the result to the user

```sh
katachi agent summary
```

**Return the output to the user exactly as it is. Do not compose the text yourself.** The output is in the language you chose with `init`'s `--lang`; there is no need to translate it.

#### Before summary — record template proposals

**This is a judgment call for you.** In katachi a `.template { }` can be attached to any file declaration in `layout { }`, and `:architecture-test:katachiTemplate` generates it **exactly where that declaration says it goes**. As "what you can do next" after installation, propose which Roles to give a template, and what it would contain.

Work from `roles` in the step 1 report (`katachi agent data get report roles`). **The good candidates are Roles with many files of the same shape** (UseCase, ViewModel, Screen, Repository, and so on). Do not propose one for a Role with a small `count`, or one whose files each take a different shape.

**Roles whose destination directory is a wildcard, like `feature/*/...`, are candidates too** (ViewModel and Screen are usually shaped this way). Name that `*` with `capture("feature")` and you can pick the destination with `--arg feature=home` (step 2 of 6-D). **Every `*` left in the destination needs a name, both the module directory's and the package directory's.** If the two get the same value, give both one name (write `capture("feature")` in two places). The only thing that can't be named is `**` (a partial match can be named with `capture()`); don't propose one for a Role whose destination is only that.

For each Role, decide the following and record it with `add template`.

| Field | Content |
|---|---|
| `--role` | The Role's name (as written in `architecture { }`) |
| `--files` | The files it creates. **Can be passed more than once.** Write them with the parameter embedded, like `${name}UseCase.kt` (only when one kind of file has several name shapes; files of different kinds, like an interface and its implementation, get separate Roles and one `add template` each) |
| `--params` | The parameter names taken with `stringParameter()` or another `*Parameter()`, plus the name given to a wildcard in the destination (for example, `feature`). Can be passed more than once. **List as many as the names you gave to the `*` in the destination** (one, if you gave the module directory and the package directory the same name) |
| `--basedOn` | The existing file the skeleton is based on (one of `roles[].examples`) |
| `--reason` | Why this Role gets one (the count, and that the files share a shape) |

```sh
katachi agent add template --role UseCase --files '${name}UseCase.kt' --params name --basedOn "domain/src/main/kotlin/com/example/app/domain/useCase/GetUserUseCase.kt" --reason "All 18 are interfaces and share one shape"
katachi agent add template --role UseCaseImplementation --files '${name}UseCaseImpl.kt' --params name --basedOn "data/src/main/kotlin/com/example/app/data/useCase/GetUserUseCaseImpl.kt" --reason "All 18 implement a UseCase and share one shape"
katachi agent add template --role ViewModel --files '${name}ViewModel.kt' --params feature --params name --basedOn "feature/home/src/main/kotlin/com/example/app/feature/home/HomeViewModel.kt" --reason "12 are one each in every feature/* module, and share one shape. The * in feature/* and the * of the package directory take the same name, so one name, feature, picks the destination"
```

If you judge that no Role suits a template, record nothing. What you record shows up in the "Next action" of `summary` and in the report's "Template proposals". **Do not write `.template { }` here.** That happens in 6-D, after the user agrees.

If incomplete items remain, the failure format is printed; if everything is done, the success format is printed. Both formats end with a "Resume" field (the section the next session reads). The number of violations, the number of questions and how many of them are answered, and the number of changed files are pulled from the JSON.

The only thing you write out in prose after `summary`'s output is **the unanswered questions** (`summary` lists them as `- <id> <question> → recommended: <recommendation>`). Leave the answered ones as a count. **This part is meant for a human to read and decide on, so as an exception you write the text yourself here.** Everywhere else, use `summary`'s output as is. For the answers, show the URL of the report for ids starting with `cq` and of the checklist for ids starting with `q`, have them written in the browser, then import them with `katachi agent answers import <json>` (an answer that arrives in chat goes through `answers set`, which writes where the id belongs, as in 1-2).

> **The session may end here.** The next session runs `katachi agent summary` in the root of the project, and reads the section named in the "Resume" field of the output (its file is in the table in `references/setup/README.md`; `katachi agent guide <section ID>` prints the same text). The previous session's results are in the checklist and the report. Do not rely on the conversation's memory.
