/**
 * Starlight のルートミドルウェア（astro.config.mjs の `routeMiddleware`）。古い版のページだけに効く。
 *
 * - 検索の索引から外す。検索は starlight-theme-nova のものを使っていて版で絞れないので、
 *   入れると同じページが最新版と v0.2 で二重に出る。古い版のページでも検索は最新版を引く。
 * - サイドバーの public/ 行きのリンク（API リファレンス）を最新のものへ向け直す。
 *   starlight-versions は写しのサイドバーの `link` にも版の区切りを足すので、そのままだと
 *   `/katachi/0.2/api-docs/` という無いページを指す。public/ の下は版を持たない。
 *
 * 本文の中のリンクは src/middleware.ts が直す（描画後の HTML でないと届かないため）。
 */
import { defineRouteMiddleware, type StarlightRouteData } from '@astrojs/starlight/route-data';
import { archivedVersionOf, unversionPublicPath } from './version-links';

type SidebarEntry = StarlightRouteData['sidebar'][number];

const fixSidebar = (entries: SidebarEntry[]) => {
	for (const entry of entries) {
		if (entry.type === 'group') fixSidebar(entry.entries);
		else entry.href = unversionPublicPath(entry.href) ?? entry.href;
	}
};

export const onRequest = defineRouteMiddleware(async (context, next) => {
	// starlight-versions のミドルウェア（サイドバーを版ごとに切り替える）の後に手を入れる。
	await next();

	if (!archivedVersionOf(context.url.pathname)) return;
	const route = context.locals.starlightRoute;

	route.entry = { ...route.entry, data: { ...route.entry.data, pagefind: false } };
	fixSidebar(route.sidebar);
});
