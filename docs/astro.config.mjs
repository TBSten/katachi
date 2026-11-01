// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightThemeNova from 'starlight-theme-nova';
import mermaid from 'astro-mermaid';
import starlightLlmsTxt from 'starlight-llms-txt';
// 内部リンクの切れをビルドで落とす。腐ったリンクを公開しないための歯止め。
import starlightLinksValidator from 'starlight-links-validator';
// ドキュメントの版。版の一覧は src/lib/versions.mjs にある（版を足すときに直すのはそこだけ）。
import starlightVersions from 'starlight-versions';
import { archivedVersions, archivedVersionSlugs, currentVersion, publicSections } from './src/lib/versions.mjs';
// `astro dev` のときだけ dev toolbar に入るフィードバックモード。本番ビルドには何も出ない。
import feedback from './src/integrations/feedback/index.ts';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const sidebar = (() => {
	/** @typedef {{ en: string; ja: string; }} Label */
	const translated = (/** @type {Label} */ label) => ({ label: label.en, translations: { ja: label.ja } })
	const category = (
		/** @type {Label} */ label,
		/** @type {Array<ReturnType<typeof item> | ReturnType<typeof link>>} */ ...items
	) => ({ ...translated(label), items })
	const item = (/** @type {string} */ slug, /** @type {Label} */ label) => ({ ...translated(label), slug })
	const link = (/** @type {string} */ href, /** @type {Label} */ label) => ({ ...translated(label), link: href })

	return [
		category(
			{ en: 'Get started', ja: 'はじめる' },
			item('get-started/motivation', { en: 'Motivation', ja: 'モチベーション' }),
			item('get-started/first-architecture', { en: 'Your first definition', ja: '初めての定義' }),
			item('get-started/faq', { en: 'FAQ', ja: 'FAQ' }),
			item('get-started/comparison-with-other-tools', { en: 'Compared with other tools', ja: '他ツールとの比較' }),
		),
		category(
			{ en: 'Guides', ja: 'ガイド' },
			item('guides/basic-api', { en: 'The basic API', ja: '基本的な API' }),
			item('guides/role', { en: 'Role', ja: 'Role' }),
			item('guides/layout', { en: 'Layout', ja: 'Layout' }),
			item('guides/kt-file-content', { en: 'ktFileContent', ja: 'ktFileContent' }),
			item('guides/document-generation', { en: 'Document generation', ja: 'ドキュメント生成' }),
			item('guides/generate-code-from-template', { en: 'Generate code from template', ja: 'テンプレートからコード生成' }),
			item('guides/baseline', { en: 'Baseline', ja: 'Baseline' }),
			item('guides/processor', { en: 'ArchitectureProcessor', ja: 'ArchitectureProcessor とそのカスタマイズ' }),
		),
		category(
			{ en: 'Recipes', ja: 'レシピ' },
			item('recipes', { en: 'All recipes', ja: '一覧' }),
			item('recipes/android-three-layer', { en: 'Android three layers architecture', ja: 'Android の3層アーキテクチャ' }),
			item('recipes/gradle', { en: 'Gradle', ja: 'Gradle' }),
			item('recipes/ai-agent', { en: 'AI agents', ja: 'AI Agent' }),
			item('recipes/ktlint', { en: 'ktlint', ja: 'ktlint' }),
			item('recipes/detekt', { en: 'detekt', ja: 'detekt' }),
			item('recipes/git', { en: 'Git', ja: 'Git' }),
			item('recipes/github', { en: 'GitHub', ja: 'GitHub' }),
		),
		link('/api-docs/', { en: 'API reference', ja: 'API リファレンス' }),
		item('roadmap', { en: 'Roadmap', ja: 'ロードマップ' }),
	]
})()

// `astro dev` は public/ の下のディレクトリの URL（`/katachi/api-docs/`）を index.html に
// 解決せず 404 を返す。GitHub Pages とビルドしたサイトでは解決されるので、サイドバーの
// 「API リファレンス」が dev サーバーでだけリンク切れになっていた。dev のときだけ、
// public/ に `<dir>/index.html` があるディレクトリの URL を index.html へ書き換える。
const publicDir = fileURLToPath(new URL('./public/', import.meta.url));
const publicDirectoryIndex = {
	name: 'katachi-public-directory-index',
	apply: 'serve',
	// Astro の dev ハンドラより先に登録しないと、その 404 が先に返る。
	enforce: 'pre',
	configureServer(server) {
		server.middlewares.use((req, res, next) => {
			// Vite has already taken `base` off the URL by the time it gets here (`/api-docs/`).
			const path = (req.url ?? '').split('?')[0].replace(/^\/katachi(?=\/)/, '');
			if (path.endsWith('/') && path !== '/') {
				const index = `${publicDir}${decodeURIComponent(path.slice(1))}index.html`;
				if (existsSync(index) && statSync(index).isFile()) {
					res.setHeader('Content-Type', 'text/html; charset=utf-8');
					res.end(readFileSync(index));
					return;
				}
			}
			next();
		});
	},
};

// MDX の `import { LinkCard } from '@astrojs/starlight/components'` を、OGP 画像つきの
// src/components/LinkCard.astro に差し替える。本文（MDX）を書き換えずに済み、dev でも build でも効く。
// 入口の starlight-components.ts と LinkCard.astro 自身が読む元の入口は差し替えない（無限ループ回避）。
const linkCardEntry = fileURLToPath(new URL('./src/components/starlight-components.ts', import.meta.url));
const linkCardImpl = fileURLToPath(new URL('./src/components/LinkCard.astro', import.meta.url));
const katachiLinkCardOverride = {
	name: 'katachi-link-card-override',
	enforce: 'pre',
	resolveId(source, importer) {
		if (source !== '@astrojs/starlight/components') return null;
		if (importer && [linkCardEntry, linkCardImpl].includes(importer.split('?')[0])) return null;
		return linkCardEntry;
	},
};

// https://astro.build/config
export default defineConfig({
	// GitHub Pages の project site として公開する: https://tbsten.github.io/katachi/
	// `site` は `starlight-llms-txt` が絶対 URL を組むのに要る（無いと動かない）。
	// `base` は sitemap・llms*.txt・`slug` 由来のリンクすべてに効く。
	site: 'https://tbsten.github.io',
	base: '/katachi',
	integrations: [
		feedback(),
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
				'Declare your Android/KMP project architecture in a Kotlin DSL and check the whole tree against it, deny by default.',
			// タイトルの上に、frontmatter の tags（難易度、定義は tags.yml）をラベルで出す。
			// リンクにはしない自前描画（DifficultyTag.astro）。starlight-tags は使っていない
			// — v2.0.0 は /tags/ 系のページを常に生成し、日本語側でそのリンクが
			// `/katachi/ja/ja/...` と locale が二重になって 404 になる不具合があったため。
			//
			// starlight-versions は PageTitle・ThemeSelect・Search・Banner を差し替えて版の表示を足すが、
			// PageTitle（ここ）と ThemeSelect・Search（starlight-theme-nova）は既に差し替え済みなので、
			// プラグインは「上書き済み」の警告を出して手を引く。代わりに次のようにしている。
			// - PageTitle: 自前の PageTitle.astro が「古い版です」の注意（VersionNotice）を出す。
			// - ThemeSelect: 自前の ThemeSelect.astro が版の切り替え（VersionSelect）と nova のテーマ切り替えを並べる。
			// - Search: nova のまま。古い版のページは検索の索引に入れない（src/lib/route-data.ts）。
			components: {
				PageTitle: './src/components/overrides/PageTitle.astro',
				ThemeSelect: './src/components/overrides/ThemeSelect.astro',
			},
			// 古い版のページを検索の索引から外し、サイドバーの public/ 行きのリンクを直す。
			routeMiddleware: './src/lib/route-data.ts',
			// `locales` のキーで指す。ja は root なので 'root'。
			defaultLocale: 'root',
			// デザインシステムは starlight-theme-nova に任せる。
			plugins: [
				starlightThemeNova(),
				// /llms.txt・/llms-full.txt・/llms-small.txt を生成する。
				// 古い版の写しは llms-small.txt から外し、llms-full.txt では末尾へ回す
				// （llms-full.txt には除外の設定が無い）。
				starlightLlmsTxt({
					exclude: archivedVersionSlugs.map((slug) => `${slug}/**`),
					demote: archivedVersionSlugs.map((slug) => `${slug}/**`),
				}),
				// 内部リンクが切れていたらビルドを落とす。腐ったリンクを公開しないための歯止め。
				// 相対リンクも許す（public/ のスライドへは ../../slides/... で張っている）。
				// トップページは content collection ではなく src/pages/ の .astro なので、validator は
				// 存在を知らず「custom page への不正なリンク」として落とす。そこだけ検査から外す。
				starlightLinksValidator({
					errorOnRelativeLinks: false,
					//
					// 古い版の写しの中の public/ 行きのリンク（`/katachi/0.2/api-docs/...`）も外す。starlight-versions が
					// 版の区切りを足してしまったもので、表示の手前で src/middleware.ts が `/katachi/api-docs/...` に戻す。
					exclude: [
						'/katachi/',
						'/katachi/ja/',
						...archivedVersionSlugs.flatMap((slug) =>
							['', 'ja/'].map((locale) => `/katachi/${locale}${slug}/{${publicSections.join(',')}}/**`),
						),
					],
				}),
				// ドキュメントの版。src/content/docs/ 直下がいま書いている版（URL は今までどおり）、
				// 凍結した版は src/content/docs/<slug>/ と src/content/docs/ja/<slug>/ の写し。
				// `versions` に無い版の写しが無ければ、dev / build の開始時に今の docs から写しを作る。
				// starlight-theme-nova より後ろに置くこと（nova の差し替えを見て警告を出すため。上の components を参照）。
				starlightVersions({
					versions: archivedVersions,
					current: currentVersion,
				}),
			],
			// Expressive Code の設定（長いコードブロックを畳むプラグイン）は ec.config.mjs にある。
			// トップページ（src/components/home/Home.astro）が使う `<Code>` コンポーネントは、ここに
			// JSON にできないオプション（プラグインの関数）があると描画できないため、外へ出した。
			// `true` は消さないこと。starlight-theme-nova は未指定だと Expressive Code を切る。
			expressiveCode: true,
			// CodeComparison のレイアウト。scoped style だとドット記法のコンポーネントに
			// スタイルが伝播しないため、ここでグローバルに読ませている。
			customCss: [
				'./src/styles/code-comparison.css',
				'./src/styles/custom-global.css',
				'./src/styles/home.css',
			],
			// 英語が既定で `/katachi/` 配下、日本語は `/katachi/ja/` 配下。
			// 訳が無いページは Starlight が既定ロケールで代替し、その旨の注意を出す。
			locales: {
				root: { label: 'English', lang: 'en' },
				ja: { label: '日本語', lang: 'ja' },
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
			// 中身はファイル先頭の `sidebar`（ラベルを { en, ja } で書くヘルパー）。
			sidebar,
		}),
	],
	vite: {
		plugins: [publicDirectoryIndex, katachiLinkCardOverride],
		build: {
			// 500 kB を超えるのは Mermaid の描画コードのチャンク（最大 1.5 MB）。astro-mermaid が
			// 図のあるページでだけ動的に読むので、ほかのページの読み込みは重くならない。
			chunkSizeWarningLimit: 2000,
			rolldownOptions: {
				// Astro が MDX ごとに差し込む "use astro:head-inject" に、rolldown がページの数だけ
				// MODULE_LEVEL_DIRECTIVE を出す。Astro の内部の印で、こちらでは直せない。
				onLog(level, log, handler) {
					if (log.code === 'MODULE_LEVEL_DIRECTIVE' && log.message.includes('astro:head-inject')) return;
					handler(level, log);
				},
			},
		},
	},
	server: {
		host: true,
	},
});
