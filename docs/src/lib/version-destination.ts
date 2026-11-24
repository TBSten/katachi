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

/**
 * 開発中の版のページから案内する、最新のリリース済みの版（`version`）の同じページへの行き先。
 *
 * - 公開サイトの開発版（`base` が `/katachi/dev`）: リリース済みの docs は `releasedBase`（`/katachi`）の直下にある。
 *   そこは別のビルドでページの一覧が分からないので、このビルドの中の写し（`/katachi/dev/0.4/...`）に
 *   同じページがあれば同じページへ、無ければ（版を切ったあとに足したページ）トップへ送る。
 * - それ以外（手元の `pnpm dev`・`pnpm run build`。`base` が `releasedBase` と同じ）: リリース済みの docs は
 *   このビルドの中の写しにしか無いので、写しへ送る（`resolveVersionPath` と同じ決め方）。
 */
export function latestReleasedPath(
	target: Target & { version: string; releasedBase: string },
	available: ReadonlySet<string>,
	firstPage?: string,
): string {
	const { releasedBase, ...copy } = target;
	if (copy.base === releasedBase) return resolveVersionPath(copy, available, firstPage);
	const slug = available.has(pagePath(copy)) ? copy.slug : '';
	return pagePath({ base: releasedBase, locale: copy.locale, slug });
}
