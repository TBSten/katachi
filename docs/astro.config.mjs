// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightThemeNova from 'starlight-theme-nova';
import mermaid from 'astro-mermaid';
import { pluginCollapsible } from 'expressive-code-collapsible';
import starlightLlmsTxt from 'starlight-llms-txt';
// ページに難易度のタグを付け、タグごとの一覧ページを自動生成する。定義は tags.yml。
import starlightTagsPlugin from 'starlight-tags';
// 内部リンクの切れをビルドで落とす。腐ったリンクを公開しないための歯止め。
import starlightLinksValidator from 'starlight-links-validator';

// https://astro.build/config
export default defineConfig({
	// GitHub Pages の project site として公開する: https://tbsten.github.io/katachi/
	// `site` は `starlight-llms-txt` が絶対 URL を組むのに要る（無いと動かない）。
	// `base` は sitemap・llms*.txt・`slug` 由来のリンクすべてに効く。
	site: 'https://tbsten.github.io',
	base: '/katachi',
	integrations: [
		// starlight より前に置くこと。starlight が markdown を処理する前に
		// ```mermaid フェンスを差し替える必要がある。
		mermaid({ theme: 'neutral', autoTheme: true }),
		starlight({
			title: 'katachi',
			// ブラウザのタブ。SVG なのでどの解像度でも潰れない。
			// PNG 系（apple-touch-icon / icon-192 / icon-512）は下の `head` で足す。
			favicon: '/favicon.svg',
			// ヘッダのマーク。タイトル文字は残す（ロゴだけだと読み上げで名前が消える）。
			logo: {
				src: './src/assets/katachi-mark.svg',
				alt: '',
			},
			description:
				'Android / KMP プロジェクトのアーキテクチャを Kotlin DSL で書き、同じ定義から「テスト」と「ドキュメント」の両方を出すライブラリ。',
			// `locales` のキーで指す。ja は root なので 'root'。
			defaultLocale: 'root',
			// デザインシステムは starlight-theme-nova に任せる。
			plugins: [
				starlightThemeNova(),
				// /llms.txt・/llms-full.txt・/llms-small.txt を生成する。
				starlightLlmsTxt(),
				// 難易度タグ。サイドバーには出さない（ガイドの並びを崩さないため）。
				// /tags/ に一覧、/tags/<id>/ に各タグのページが出る。定義は tags.yml。
				starlightTagsPlugin({ sidebar: false }),
				// 内部リンクが切れていたらビルドを落とす。腐ったリンクを公開しないための歯止め。
				starlightLinksValidator(),
			],
			// 長いコードブロックを畳む。手触りの検証中で、しきい値は暫定。
			expressiveCode: {
				plugins: [
					pluginCollapsible({
						lineThreshold: 15,
						previewLines: 8,
						defaultCollapsed: true,
						expandButtonText: 'すべて表示',
						collapseButtonText: '折りたたむ',
					}),
				],
			},
			// CodeComparison のレイアウト。scoped style だとドット記法のコンポーネントに
			// スタイルが伝播しないため、ここでグローバルに読ませている。
			customCss: [
				'./src/styles/code-comparison.css',
				'./src/styles/custom-global.css',
				'./src/styles/home.css',
			],
			// 日本語が既定で `/katachi/` 配下、英語は `/katachi/en/` 配下。
			// 英訳が無いページは Starlight が日本語で代替し、その旨の注意を出す。
			locales: {
				root: { label: '日本語', lang: 'ja' },
				en: { label: 'English', lang: 'en' },
			},
			// OGP。Starlight は og:title / og:description / og:url までは自分で出すが、
			// 画像は出さない。`twitter:card` が summary_large_image なので、画像が無いと
			// カードが小さい形に落ちる。
			//
			// 画像は `public/og.png`（1200×630）。`site` + `base` を含めた絶対 URL で
			// 指す必要がある — OGP は相対パスを解決しない。
			head: [
				// iOS のホーム画面と Android の追加用。favicon の SVG では拾われない。
				{
					tag: 'link',
					attrs: { rel: 'apple-touch-icon', sizes: '180x180', href: '/katachi/apple-touch-icon.png' },
				},
				{
					tag: 'link',
					attrs: { rel: 'icon', type: 'image/png', sizes: '192x192', href: '/katachi/icon-192.png' },
				},
				{
					tag: 'link',
					attrs: { rel: 'icon', type: 'image/png', sizes: '512x512', href: '/katachi/icon-512.png' },
				},
				// モバイルのアドレスバーの色。アイコンの背景と揃える。
				{
					tag: 'meta',
					attrs: { name: 'theme-color', content: '#a2ccf7' },
				},
				{
					tag: 'meta',
					attrs: { property: 'og:image', content: 'https://tbsten.github.io/katachi/og.png' },
				},
				{
					tag: 'meta',
					attrs: { property: 'og:image:width', content: '1200' },
				},
				{
					tag: 'meta',
					attrs: { property: 'og:image:height', content: '630' },
				},
				{
					tag: 'meta',
					attrs: { property: 'og:image:alt', content: 'katachi — Deny by default architecture testing' },
				},
				{
					tag: 'meta',
					attrs: { name: 'twitter:image', content: 'https://tbsten.github.io/katachi/og.png' },
				},
			],
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/tbsten/katachi' },
			],
			// サイドバーは手書き（`autogenerate` はやめた）。順序を人が決める形にしている。
			// `index`（トップページ、`src/content/docs/index.mdx`）はサイドバーに出さない。
			// サイトタイトル / ロゴから辿れるので十分という判断。ページ自体は残っている。
			sidebar: [
				{
					label: 'はじめる',
					translations: { en: 'Get started' },
					items: [
						{ label: 'モチベーション', translations: { en: 'Motivation' }, slug: 'get-started/motivation' },
						{ label: '初めての定義', translations: { en: 'Your first definition' }, slug: 'get-started/first-architecture' },
						{ label: 'FAQ', translations: { en: 'FAQ' }, slug: 'get-started/faq' },
						{ label: '他ツールとの比較', translations: { en: 'Compared with other tools' }, slug: 'get-started/comparison-with-other-tools' },
					],
				},
				{
					// もとはコンセプトとガイドの2節だった。Role と Layout を読んだ人がそのまま
					// konsist / processor へ進めるようにするため、間で節を切らない。
					label: 'ガイド',
					translations: { en: 'Guides' },
					items: [
						// この節の入口。Role と Layout はこの地図の深掘りなので、必ず先頭に置く。
						// 下の「API リファレンス」（Dokka の全一覧）とは別物で、あちらが網羅、こちらが最小。
						{ label: '基本的な API', translations: { en: 'The basic API' }, slug: 'guides/basic-api' },
						// 「Role 定義を分割する」は独立ページをやめ、Role の末尾へ統合した。
						{ label: 'Role', translations: { en: 'Role' }, slug: 'guides/role' },
						{ label: 'Layout', translations: { en: 'Layout' }, slug: 'guides/layout' },
						{ label: 'Konsist との統合', translations: { en: 'Konsist integration' }, slug: 'guides/konsist-integration' },
						{ label: 'ArchitectureProcessor とそのカスタマイズ', translations: { en: 'ArchitectureProcessor' }, slug: 'guides/processor' },
					],
				},
				{
					label: 'レシピ',
					translations: { en: 'Recipes' },
					items: [
						// `/recipes/` の索引。中身は `SectionIndex` がこの `items` の順番を
						// 読んで組み立てるので、ページを足すときに直すのはここだけでよい。
						{ label: '一覧', translations: { en: 'All recipes' }, slug: 'recipes' },
						{ label: 'Android の3層', translations: { en: 'Android three layers' }, slug: 'recipes/android-three-layer' },
						{ label: 'Gradle', translations: { en: 'Gradle' }, slug: 'recipes/gradle' },
						{ label: 'AI Agent', translations: { en: 'AI agents' }, slug: 'recipes/ai-agent' },
						{ label: 'ktlint', translations: { en: 'ktlint' }, slug: 'recipes/ktlint' },
						{ label: 'detekt', translations: { en: 'detekt' }, slug: 'recipes/detekt' },
						{ label: 'Git', translations: { en: 'Git' }, slug: 'recipes/git' },
						{ label: 'GitHub', translations: { en: 'GitHub' }, slug: 'recipes/github' },
					],
				},
				{
					label: 'API リファレンス',
					translations: { en: 'API reference' },
					// Dokka の出力はまだ無い。`docs/public/api-docs/index.html` のプレースホルダを
					// 外部リンクとして指す（content collection のページではないので `link` を使う）。
					//
					// `slug` は Starlight が `base` 配下へ自動で解決するが、この `link` のような
					// 素のパス文字列はしない。`base` を自分で前置する必要がある。
					link: '/katachi/api-docs/',
				},
				{ label: 'ロードマップ', translations: { en: 'Roadmap' }, slug: 'roadmap' },
			],
		}),
	],
});
