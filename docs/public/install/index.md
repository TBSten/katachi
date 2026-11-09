# Installing katachi

This page exists so that `<base-url>/install/` is not a dead end. The instructions
themselves are per language.

- **English** — https://tbsten.github.io/katachi/install/en/index.md
- **日本語** — https://tbsten.github.io/katachi/install/ja/index.md

Both run on the `katachi` command, which also ships the guides:

```sh
brew install tbsten/tap/katachi
katachi agent guide --lang en   # or --lang ja: the table of contents, before init too
```

The language decides which guide, checklist and report templates `katachi agent init` puts in the working directory, and the language of the command's output. Pass it to `init` once; later commands read it from there.

`katachi-install.sh` (the script of 0.3.0 and earlier) is deprecated and will be removed in 0.5.0.

Docs: https://tbsten.github.io/katachi/
