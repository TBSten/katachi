/**
 * 版の切り替え（VersionSelect.astro）の行く先を決める。
 *
 * starlight-versions の切り替えは、今のパスに版の区切りを付け替えるだけで、行く先のページがあるかを見ない。
 * そのため、その版に無いページ（トップページ・版を切ったあとに足したページ）から切り替えると 404 になる。
 * ここでは、ビルド時に分かる「公開されるページの一覧」に行く先があるかを見て、無ければ
 * その版のトップ、それも無ければその版の最初のページへ向ける。
 */

type Sidebar = { slug?: string; items?: Sidebar[] }[];

/** 版のサイドバー定義（src/content/versions/<slug>.json の `sidebar`）で、最初に出てくるページの slug。 */
export function firstSlug(sidebar: Sidebar | undefined): string | undefined {
	for (const entry of sidebar ?? []) {
		const slug = entry.slug ?? firstSlug(entry.items);
		if (slug) return slug;
	}
	return undefined;
}

/**
 * ページの content id（`guides/role`、`0.2/guides/role`、`ja/0.2/guides/role`）から、
 * 版の区切りと locale を外したページの slug（`guides/role`）を返す。
 */
export function pageSlug(id: string, locale: string | undefined, versionSlugs: readonly string[]): string {
	const segments = id.split('/');
	if (locale && segments[0] === locale) segments.shift();
	if (segments[0] && versionSlugs.includes(segments[0])) segments.shift();
	return segments.join('/');
}

type Target = { base: string; locale?: string; version?: string; slug: string };

/** base 付きの、末尾が `/` のページの絶対パス。`version` が無ければ最新版。 */
export function pagePath({ base, locale, version, slug }: Target): string {
	const segments = [locale, version, ...slug.split('/')].filter(Boolean);
	return `${base}/${segments.map((segment) => `${segment}/`).join('')}`;
}

/**
 * `target` のページへの行き先を返す。ページが無ければ、その版のトップ、無ければ `firstPage`（その版の最初のページ）。
 * @param available 公開されるページの絶対パス（`pagePath` の形）の一覧。
 * @param firstPage 版のトップも無いときの行く先の slug。
 */
export function resolveVersionPath(target: Target, available: ReadonlySet<string>, firstPage?: string): string {
	const same = pagePath(target);
	if (available.has(same)) return same;
	const top = pagePath({ ...target, slug: '' });
	if (available.has(top)) return top;
	return firstPage ? pagePath({ ...target, slug: firstPage }) : same;
}
