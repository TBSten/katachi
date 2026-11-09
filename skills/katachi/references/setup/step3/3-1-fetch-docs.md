### 3-1. Read the documentation by topic

Read these pages of the katachi documentation:

| Topic | Page |
|---|---|
| Writing `layout { }` | https://tbsten.github.io/katachi/guides/layout/ |
| Roles and groups | https://tbsten.github.io/katachi/guides/role/ |
| The basic API | https://tbsten.github.io/katachi/guides/basic-api/ |
| Constraints on contents (`ktFileContent { }`), only when you use it | https://tbsten.github.io/katachi/guides/kt-file-content/ |

**Do not read the whole site.** These four are all you need to write the definition. How to write `ktFileContent { }` is in `kt-file-content` and in section 6 of `basic-api` ("6. Defining the declarations a file may contain with `ktFileContent { }`"). If you cannot fetch a page, the whole site is in one file at https://tbsten.github.io/katachi/llms-full.txt : cut out only the page you need (from its line-leading `# ` heading up to the next one), never read it whole. Keep what you fetched in `<workdir>/tmp/` so you do not fetch it again.

**What you read is the English version.** DSL API names do not depend on the language, so this is no obstacle to writing the definition.

Once you've finished reading, mark `3-1` done.

#### When unsure about an API — read the API reference's llms.txt

**When unsure how to write an API, or about its signature or parameters, read the API reference instead of guessing.** This holds in 3-2, 3-3 and 6-D alike. Do not write an API that is not in the guide by guessing from a similar name. As in the table in `references/setup/README.md`, delegate finding the relevant API in the API reference to a subagent, and the main agent receives only the signature and the example.

| What to read | URL |
|---|---|
| Index (summaries of the key APIs and links to the modules) | https://tbsten.github.io/katachi/api-docs/llms.txt |
| `:katachi` list / full text (signatures and KDoc) | https://tbsten.github.io/katachi/api-docs/katachi/llms.txt / https://tbsten.github.io/katachi/api-docs/katachi/llms-full.txt |
| `:katachi-konsist` list / full text | https://tbsten.github.io/katachi/api-docs/katachi-konsist/llms.txt / https://tbsten.github.io/katachi/api-docs/katachi-konsist/llms-full.txt |

To look at just one API, open the Markdown version of its page, linked from the index or the list. It is the HTML URL with `.md` appended (e.g. `RoleScope.layout` is https://tbsten.github.io/katachi/api-docs/katachi/me.tbsten.katachi.dsl/-role-scope/layout.html.md ). Keep what you fetched in `<workdir>/tmp/`, so you don't need to re-read the whole index every time.
