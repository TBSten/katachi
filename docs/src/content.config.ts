import { defineCollection } from 'astro:content';
import { docsLoader, i18nLoader } from '@astrojs/starlight/loaders';
import { docsSchema, i18nSchema } from '@astrojs/starlight/schema';
import { z } from 'astro/zod';
import { docsVersionsLoader } from 'starlight-versions/loader';

export const collections = {
	docs: defineCollection({
		loader: docsLoader(),
		// `tags:` を frontmatter に書けるようにする。難易度タグ1つを id で指す
		// （id の定義は docs/tags.yml）。表示は DifficultyTag.astro が自前で行う。
		schema: docsSchema({ extend: z.object({ tags: z.array(z.string()).optional() }) }),
	}),
	// 凍結した版ごとのサイドバー（src/content/versions/<slug>.json）。starlight-versions が写しと一緒に作る。
	versions: defineCollection({ loader: docsVersionsLoader() }),
	// UI 文言の上書き（src/content/i18n/<locale>.json）。starlight-versions に日本語の文言が無いので足している。
	i18n: defineCollection({ loader: i18nLoader(), schema: i18nSchema() }),
};
