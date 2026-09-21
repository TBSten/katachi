// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightThemeNova from 'starlight-theme-nova';

// https://astro.build/config
export default defineConfig({
	// TODO: GitHub Pages is the decided target, but publishing is still far off, so
	// `site` / `base` stay unset for now. Setting `base` rewrites every internal link,
	// so it lands together with the deploy workflow rather than ahead of it. Expect
	// `site: 'https://tbsten.github.io'` and `base: '/katachi'`. Until then the sitemap
	// is skipped, which is better than publishing one that points at the wrong host.
	integrations: [
		starlight({
			title: 'katachi',
			description:
				'Android / KMP プロジェクトのアーキテクチャを Kotlin DSL で書き、同じ定義から「テスト」と「ドキュメント」の両方を出すライブラリ。',
			defaultLocale: 'ja',
			// デザインシステムは starlight-theme-nova に任せる。
			plugins: [starlightThemeNova()],
			// CodeComparison のレイアウト。scoped style だとドット記法のコンポーネントに
			// スタイルが伝播しないため、ここでグローバルに読ませている。
			customCss: ['./src/styles/code-comparison.css'],
			locales: {
				root: { label: '日本語', lang: 'ja' },
			},
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/tbsten/katachi' },
			],
			// サイドバーは手書き（`autogenerate` はやめた）。順序を人が決める形にしている。
			// `index`（トップページ、`src/content/docs/index.mdx`）はサイドバーに出さない。
			// サイトタイトル / ロゴから辿れるので十分という判断。ページ自体は残っている。
			sidebar: [
				{
					label: 'はじめる',
					items: [
						{ label: 'モチベーション', slug: 'get-started/motivation' },
						{ label: '初めての定義', slug: 'get-started/first-architecture' },
						{ label: 'FAQ', slug: 'get-started/faq' },
					],
				},
				{ label: 'インストール', slug: 'install' },
				{
					label: 'コンセプト',
					items: [
						{ label: 'Deny by default', slug: 'concepts/deny-by-default' },
						{ label: 'Role', slug: 'concepts/role' },
						{ label: 'Layout', slug: 'concepts/layout' },
					],
				},
				{
					label: 'ガイド',
					items: [
						{ label: 'Role 定義を分割する', slug: 'guides/split-roles' },
						{ label: 'Konsist Integration', slug: 'guides/konsist-integration' },
						{ label: 'Processor のカスタマイズ', slug: 'guides/processor' },
					],
				},
				{
					label: 'レシピ',
					items: [
						{ label: 'Android の3層', slug: 'recipes/android-three-layer' },
						{ label: 'feature モジュール分割', slug: 'recipes/feature-module-split' },
						{ label: 'KMP の sourceSet', slug: 'recipes/kmp-source-set' },
						{ label: 'Gradle 周辺', slug: 'recipes/gradle' },
						{ label: 'Ktor サーバサイド', slug: 'recipes/ktor-server' },
						{ label: 'KSP プロセッサ', slug: 'recipes/ksp-processor' },
						{ label: 'Kotlin コンパイラプラグイン', slug: 'recipes/kotlin-compiler-plugin' },
						{ label: 'IntelliJ プラグイン', slug: 'recipes/intellij-plugin' },
					],
				},
				{
					label: 'API リファレンス',
					// Dokka の出力はまだ無い。`docs/public/api-docs/index.html` のプレースホルダを
					// 外部リンクとして指す（content collection のページではないので `link` を使う）。
					link: '/api-docs/',
					// `base` を設定すると（GitHub Pages 公開時に想定: `base: '/katachi'`）、
					// Starlight の `slug` は自動でその配下に解決されるが、この `link` のような
					// 素のパス文字列はズレる。公開時は `${import.meta.env.BASE_URL}api-docs/`
					// のように base を差し込む形に直すこと。
				},
				{ label: 'ロードマップ', slug: 'roadmap' },
			],
		}),
	],
});
