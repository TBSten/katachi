// ドキュメントの版（starlight-versions）の定義。astro.config.mjs・ルートミドルウェア・
// SectionIndex.astro がここを読む。版を足すときに直すのはこのファイルだけ。
//
// - `archivedVersions`: 凍結した版。`slug` が URL の区切り（/katachi/0.2/...、/katachi/ja/0.2/...）と
//   写しの置き場（src/content/docs/0.2/、src/content/docs/ja/0.2/、src/content/versions/0.2.json）になる。
//   新しい版は配列の先頭に足す（版の切り替えの並び順がこの順になる）。
// - `currentVersion`: src/content/docs/ 直下の、いま書いている版。URL に版の区切りは入らない。
//
// 新しい版の切り方は docs/AGENTS.md の「ドキュメントの版」の節にある。

/** @type {{ slug: string; label: string }[]} */
export const archivedVersions = [{ slug: '0.2', label: 'v0.2' }];

/** @type {{ label: string }} */
export const currentVersion = { label: 'v0.3' };

/** 凍結した版の slug。 */
export const archivedVersionSlugs = archivedVersions.map((version) => version.slug);

/**
 * public/ の直下（と public/ja/ の直下）で、ページから張られるディレクトリ。public/ の下は版を持たない。
 * 古い版のページからここへのリンクは、版の区切りを外して最新のものへ向ける（src/lib/version-links.ts）。
 */
export const publicSections = ['api-docs', 'install', 'slides'];
