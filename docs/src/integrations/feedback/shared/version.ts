// いま見ているページの docs の版と言語（ブラウザ側）。版の定義は src/lib/versions.mjs の 1 か所を読む。
import { archivedVersions, currentVersion } from '../../../lib/versions.mjs'

/** URL の `/katachi/(ja/)(0.2/)…` から版を求める。区切りが無ければ現行。 */
export function versionOfPage(): string {
	const base = import.meta.env.BASE_URL.replace(/\/$/, '')
	const segs = location.pathname.slice(base.length).split('/').filter(Boolean)
	if (segs[0] === 'ja') segs.shift()
	const archived = archivedVersions.find((v: { slug: string }) => v.slug === segs[0])
	return archived ? `${archived.label}（凍結版）` : `${currentVersion.label}（現行）`
}
