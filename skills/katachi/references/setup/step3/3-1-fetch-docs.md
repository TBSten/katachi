### 3-1. Fetch the documentation by topic

```sh
katachi agent docs --topic layout
katachi agent docs --topic role
katachi agent docs --topic basic-api
katachi agent docs --topic kt-file-content     # only when using ktFileContent { }
```

This fetches the guide cut out by topic and prints the file's path. Read that file. **Do not read the whole guide (`katachi agent docs`).** The four above are all you need to write the definition. How to write `ktFileContent { }` is in `kt-file-content` (`katachi agent docs --topic kt-file-content`) and in section 6 of `basic-api` ("6. Defining the declarations a file may contain with `ktFileContent { }`"). `katachi agent docs --topics` lists the topics. Anything already fetched is not fetched again.

**What is fetched is the English version.** The site's default locale is English, and this format is generated only for the default locale. DSL API names do not depend on the language, so this is no obstacle to writing the definition.

If `docs --topic` stops saying "This is a bug in the install kit", do not modify katachi; tell the user about the output. Read only that topic's page in the full-text file the output names, and go on.

Once you've finished reading, run `katachi agent check 3-1`.

#### When unsure about an API — read the API reference's llms.txt

**When unsure how to write an API, or about its signature or parameters, read the API reference instead of guessing.** This holds in 3-2, 3-3 and 6-D alike. Do not write an API that is not in the guide by guessing from a similar name. As in the table at the top, delegate finding the relevant API in the API reference to a subagent, and the main agent receives only the signature and the example.

| What to read | URL | Command |
|---|---|---|
| Index (summaries of the key APIs and links to the modules) | https://tbsten.github.io/katachi/api-docs/llms.txt | `katachi agent docs --api` |
| `:katachi` list / full text (signatures and KDoc) | https://tbsten.github.io/katachi/api-docs/katachi/llms.txt / https://tbsten.github.io/katachi/api-docs/katachi/llms-full.txt | `katachi agent docs --api katachi` (full text) |
| `:katachi-konsist` list / full text | https://tbsten.github.io/katachi/api-docs/katachi-konsist/llms.txt / https://tbsten.github.io/katachi/api-docs/katachi-konsist/llms-full.txt | `katachi agent docs --api katachi-konsist` (full text) |

To look at just one API, open the Markdown version of its page, linked from the index or the list. It is the HTML URL with `.md` appended (e.g. `RoleScope.layout` is https://tbsten.github.io/katachi/api-docs/katachi/me.tbsten.katachi.dsl/-role-scope/layout.html.md ). What the commands fetch stays in the working directory's `tmp/cache/`, so you don't need to re-read the whole index every time.
