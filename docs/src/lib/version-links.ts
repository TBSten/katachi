/**
 * 古い版のページ（/katachi/0.2/...、/katachi/ja/0.2/...）のリンクを、版に合わせて直す。
 *
 * starlight-versions は版の写しを作るとき、本文の Markdown のリンク（`/katachi/...` で始まるもの）と
 * サイドバーの `link` に版の区切りを足す。そのため次の2つがずれる。
 *
 * - public/ の下（API リファレンス・インストールキット・スライド）へのリンクにも版の区切りが付き、
 *   `/katachi/0.2/api-docs/` という無いページを指す。相対リンク（`../../slides/...`）も写しが1段深く
 *   なるぶん同じ所を指す。public/ の下は版を持たないので、版の区切りを外す（`unversionPublicPath`）。
 * - LinkCard の `href` は書き換えられず、最新版のページを指したまま。同じ版のページへ向ける（`versionDocsHref`）。
 */
import { archivedVersionSlugs, publicSections } from './versions.mjs';

const LOCALES = ['ja'];

const base = import.meta.env.BASE_URL.replace(/\/$/, '');

/** base を外したパスを区切り、locale の次の位置（版が入る位置）を返す。base の外なら `undefined`。 */
function split(pathname: string) {
	if (!pathname.startsWith(`${base}/`)) return undefined;
	const segments = pathname.slice(base.length + 1).split('/');
	const versionIndex = LOCALES.includes(segments[0] ?? '') ? 1 : 0;
	return { segments, versionIndex };
}

/** 古い版のページの URL（base 付きのパス）なら、その版の slug を返す。 */
export function archivedVersionOf(pathname: string): string | undefined {
	const parts = split(pathname);
	const version = parts?.segments[parts.versionIndex];
	return version && archivedVersionSlugs.includes(version) ? version : undefined;
}

/**
 * `pathname`（base 付きの絶対パス）が「版の区切り + public/ の下」なら版の区切りを外したパスを、
 * そうでなければ `undefined` を返す。
 */
export function unversionPublicPath(pathname: string): string | undefined {
	const parts = split(pathname);
	if (!parts) return undefined;
	const { segments, versionIndex } = parts;
	const version = segments[versionIndex];
	const section = segments[versionIndex + 1];
	if (!version || !archivedVersionSlugs.includes(version)) return undefined;
	if (!section || !publicSections.includes(section)) return undefined;
	segments.splice(versionIndex, 1);
	return `${base}/${segments.join('/')}`;
}

/**
 * 古い版のページ（`pageURL`）に置かれた、最新版のドキュメントのページへの絶対リンク（`href`）を、
 * 同じ版のページへ向け直す。トップページ・public/ の下・既に版の付いたリンク・外部リンクはそのまま返す。
 */
export function versionDocsHref(href: string | undefined, pageURL: URL): string | undefined {
	if (!href) return href;
	const version = archivedVersionOf(pageURL.pathname);
	if (!version) return href;
	const parts = split(href);
	if (!parts) return href;
	const { segments, versionIndex } = parts;
	const first = segments[versionIndex];
	if (!first || archivedVersionSlugs.includes(first) || publicSections.includes(first)) return href;
	segments.splice(versionIndex, 0, version);
	return `${base}/${segments.join('/')}`;
}
