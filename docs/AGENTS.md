## Development

When starting the dev server, use background mode:

```
astro dev --background
```

Manage the background server with `astro dev stop`, `astro dev status`, and `astro dev logs`.

## テーマ（未着手・予定）

デザインを刷新するときは **lucode starlight theme** を使う。

- https://lucas-labs.github.io/lucode-starlight-theme/guides/getting-started/
- リポジトリ: `lucas-labs/lucode-starlight-theme`

**いまは素の Starlight のまま。** 着手するのは status board の「ドキュメントサイト」の
**デザインシステムの刷新**のタイミング。その前に各ページの中身を揃える
（`reference/dsl.md` がステップ2・3 の実装に追いついていない）。

導入手順とパッケージ名は着手時に上の URL で確認すること。ここには写さない
（先に写すと、古くなったときに間違いだけが残る）。

## Documentation

Full documentation: https://docs.astro.build

Consult these guides before working on related tasks:

- [Adding pages, dynamic routes, or middleware](https://docs.astro.build/en/guides/routing/)
- [Working with Astro components](https://docs.astro.build/en/basics/astro-components/)
- [Using React, Vue, Svelte, or other framework components](https://docs.astro.build/en/guides/framework-components/)
- [Adding or managing content](https://docs.astro.build/en/guides/content-collections/)
- [Adding styles or using Tailwind](https://docs.astro.build/en/guides/styling/)
- [Supporting multiple languages](https://docs.astro.build/en/guides/internationalization/)
