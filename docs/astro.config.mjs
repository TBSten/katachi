// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	// TODO: set `site` (and `base` if this is served from a project subpath such as
	// GitHub Pages) once the deploy target is decided. Until then the sitemap is
	// skipped, which is better than publishing one that points at the wrong host.
	integrations: [
		starlight({
			title: 'katachi',
			description:
				'Android / KMP プロジェクトのアーキテクチャを Kotlin DSL で書き、同じ定義から「テスト」と「ドキュメント」の両方を出すライブラリ。',
			defaultLocale: 'ja',
			locales: {
				root: { label: '日本語', lang: 'ja' },
			},
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/tbsten/katachi' },
			],
			sidebar: [
				{
					label: 'はじめに',
					items: [
						{ label: 'katachi とは', slug: 'index' },
						{ label: '導入する', slug: 'guides/install' },
					],
				},
				{
					label: 'リファレンス',
					items: [{ autogenerate: { directory: 'reference' } }],
				},
			],
		}),
	],
});
