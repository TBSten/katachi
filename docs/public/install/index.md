# Installing katachi

This page exists so that `<base-url>/install/` is not a dead end. The instructions
themselves are per language.

- **English** — https://tbsten.github.io/katachi/install/en/index.md
- **日本語** — https://tbsten.github.io/katachi/install/ja/index.md

Both point to the katachi skill, which holds the steps:

```sh
gh skill install TBSten/katachi katachi
```

The same steps ship with the `katachi` command (`brew install tbsten/tap/katachi`; `katachi agent guide` prints the table of contents, before init too). The steps are in English. The language passed to `katachi agent init` decides the checklist and report templates it puts in the working directory, and the language of the command's output.

`katachi-install.sh` (the script of 0.3.0 and earlier) is deprecated and will be removed in 0.5.0.

Docs: https://tbsten.github.io/katachi/
