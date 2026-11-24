/*
 * 版の注意（src/components/VersionNotice.astro）を出す条件と、その文言。
 *
 * - リリース済みの版のページ（いまの root と、凍結した写し `/katachi/0.3/...`）には注意を出さない。
 *   どの版を見ているかは、ヘッダの版の切り替えで分かる。
 * - 開発中の版のページ（公開サイトの `/katachi/dev/...`、手元の `pnpm dev`）には、開発中であることと、
 *   最新のリリース済みの版への案内を出す。
 *
 * 文言は **日本語（ja）が原本、英語（en）は訳。** ja を先に直し、同じキーの en をその場で訳す。
 * 並びは `dev` `joiner` `latestBefore` リンク（最新のリリース済みの版のラベル）`latestAfter`。
 */

const ja = {
	dev: 'このページは開発中バージョンの内容です。',
	/** `dev` と `latestBefore` の間に入る区切り。日本語は句点で切れるので空、英語は空白。 */
	joiner: '',
	latestBefore: '最新のドキュメントは ',
	latestAfter: ' を参照してください。',
};

const en: typeof ja = {
	dev: 'This page is for the version in development.',
	joiner: ' ',
	latestBefore: 'See ',
	latestAfter: ' for the latest documentation.',
};

export const versionNoticeStrings = { ja, en };

/** `lang`（Starlight の `lang`。`ja`・`en`）の文言。知らない言語は英語。 */
export function versionNoticeText(lang: string | undefined): typeof ja {
	return lang === 'ja' ? ja : en;
}

/**
 * 開発中の版の注意を出すページか。
 * @param page.id ページの content id（`guides/role`、`ja/0.3/guides/role`）。404 には出さない。
 * @param page.currentIsDev いま書いている版が開発中か（src/lib/versions.mjs の `currentIsDev`）。
 * @param page.archivedVersion ページが凍結した写しなら、その版の slug。
 */
export function showsDevNotice(page: {
	id: string | undefined;
	currentIsDev: boolean;
	archivedVersion: string | undefined;
}): boolean {
	if (!page.currentIsDev || page.archivedVersion) return false;
	return !!page.id && page.id !== '404' && !page.id.endsWith('/404');
}
