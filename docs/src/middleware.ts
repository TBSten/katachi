/**
 * Astro のミドルウェア。古い版のページ（/katachi/0.2/...、/katachi/ja/0.2/...）の描画後の HTML で、
 * public/ の下へのリンクを版の区切りの無い今のパスへ直す。理由は src/lib/version-links.ts。
 *
 * 版の写し（src/content/docs/0.2/）は手で直さない決まりなので、表示の手前で直す。
 * 本文の Markdown のリンクも LinkCard の href も、ここなら同じように届く。
 */
import { defineMiddleware } from 'astro:middleware';
import { archivedVersionOf, unversionPublicPath } from './lib/version-links';

const urlAttribute = /(\s(?:href|src))="([^"]*)"/g;

export const onRequest = defineMiddleware(async (context, next) => {
	const response = await next();
	if (!archivedVersionOf(context.url.pathname)) return response;
	if (!response.headers.get('content-type')?.includes('text/html')) return response;

	const html = await response.text();
	const fixed = html.replace(urlAttribute, (match, attribute: string, value: string) => {
		if (/^[a-z][a-z0-9+.-]*:|^#|^\/\//i.test(value)) return match;
		const url = new URL(value.replaceAll('&amp;', '&'), context.url);
		const pathname = unversionPublicPath(url.pathname);
		return pathname ? `${attribute}="${`${pathname}${url.search}${url.hash}`.replaceAll('&', '&amp;')}"` : match;
	});
	const headers = new Headers(response.headers);
	headers.delete('content-length');
	return new Response(fixed, { status: response.status, statusText: response.statusText, headers });
});
