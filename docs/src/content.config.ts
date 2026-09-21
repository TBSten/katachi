import { defineCollection } from 'astro:content';
import { docsLoader, i18nLoader } from '@astrojs/starlight/loaders';
import { docsSchema, i18nSchema } from '@astrojs/starlight/schema';
import { ExtendDocsSchema } from 'lucode-starlight/schema';

export const collections = {
	// `extend` は lucode テーマの要求。テーマが frontmatter に足すフィールドを
	// 受け付けられるようにする（入れないとテーマ側の機能が使えない）。
	docs: defineCollection({ loader: docsLoader(), schema: docsSchema({ extend: ExtendDocsSchema }) }),
	// Declared because astro.config.mjs sets `locales`. Without it every build warns
	// that the "i18n" collection does not exist. The directory stays empty until we
	// actually translate Starlight's own UI strings.
	i18n: defineCollection({ loader: i18nLoader(), schema: i18nSchema() }),
};
