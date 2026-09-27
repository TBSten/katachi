import { defineCollection } from 'astro:content';
import { docsLoader, i18nLoader } from '@astrojs/starlight/loaders';
import { docsSchema, i18nSchema } from '@astrojs/starlight/schema';
import { z } from 'astro/zod';

export const collections = {
	docs: defineCollection({
		loader: docsLoader(),
		// `tags:` を frontmatter に書けるようにする。難易度タグ1つを id で指す
		// （id の定義は docs/tags.yml）。表示は DifficultyTag.astro が自前で行う。
		schema: docsSchema({ extend: z.object({ tags: z.array(z.string()).optional() }) }),
	}),
	// `i18n` は astro.config.mjs が `locales` を設定しているため宣言だけ残す。
	// src/content/i18n/{ja,en}.json は starlight-tags（タグ一覧ページ）が使っていた
	// UI 文字列で、そのプラグインを外した今は中身が未使用。削除は人の手で。
	i18n: defineCollection({ loader: i18nLoader(), schema: i18nSchema() }),
};
