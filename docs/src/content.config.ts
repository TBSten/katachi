import { defineCollection } from 'astro:content';
import { docsLoader } from '@astrojs/starlight/loaders';
import { docsSchema } from '@astrojs/starlight/schema';
import { z } from 'astro/zod';

export const collections = {
	docs: defineCollection({
		loader: docsLoader(),
		// `tags:` を frontmatter に書けるようにする。難易度タグ1つを id で指す
		// （id の定義は docs/tags.yml）。表示は DifficultyTag.astro が自前で行う。
		schema: docsSchema({ extend: z.object({ tags: z.array(z.string()).optional() }) }),
	}),
};
