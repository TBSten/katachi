// ドキュメントの版（starlight-versions）の定義。astro.config.mjs・ルートミドルウェア・
// SectionIndex.astro・VersionSelect.astro がここを読む。版を足すときに直すのはこのファイルだけ。
//
// - `archivedVersions`: 凍結した版。`slug` が URL の区切り（/katachi/0.2/...、/katachi/ja/0.2/...）と
//   写しの置き場（src/content/docs/0.2/、src/content/docs/ja/0.2/、src/content/versions/0.2.json）になる。
//   新しい版は配列の先頭に足す（版の切り替えの並び順がこの順になる）。
// - `currentVersion`: src/content/docs/ 直下の、いま書いている版。URL に版の区切りは入らない。
//   ラベルは手で持たず、凍結した版の先頭から「次の版 + -dev」（0.3 → v0.4-dev）を算出する。
//   リリースのビルド（タグから）では、docs.yml が `KATACHI_DOCS_VERSION_LABEL`（`v0.4`）で上書きする。
//
// 公開は .github/workflows/docs.yml が 2 回ビルドして 1 つのサイトに合成する:
// root（/katachi/）は最新のリリースタグ、/katachi/dev/ は main。環境変数で切り替える。
//
// | 環境変数                     | 意味                                                        | 省略時        |
// |------------------------------|-------------------------------------------------------------|---------------|
// | KATACHI_DOCS_BASE            | Astro の `base`。開発版のビルドで `/katachi/dev`            | `/katachi`    |
// | KATACHI_DOCS_VERSION_LABEL   | いま書いている版のラベル。リリースのビルドでタグから渡す    | 算出（v0.4-dev） |
// | KATACHI_DOCS_DEV_BASE        | 版の切り替えに足す開発版の base。リリースのビルドで渡す     | 無し（足さない） |
//
// 新しい版の切り方は docs/AGENTS.md の「ドキュメントの版」の節にある。

// このファイルはブラウザでも読まれる（docs/src/integrations/feedback/shared/version.ts）。
// ブラウザには `process` が無いので、環境変数は無いものとして読む。
const env = globalThis.process?.env ?? {};

/** @type {{ slug: string; label: string }[]} */
export const archivedVersions = [
  { slug: '0.3', label: 'v0.3' },
  { slug: '0.2', label: 'v0.2' },
];

/** 凍結した版の slug。 */
export const archivedVersionSlugs = archivedVersions.map((version) => version.slug);

/**
 * 版の slug / ラベル（`0.3`、`v0.3`、`v0.3.1`）の次の minor の開発版のラベル（`v0.4-dev`）。
 * @param {string} version
 */
export function devLabelAfter(version) {
  const match = /^v?(\d+)\.(\d+)/.exec(version);
  if (!match) throw new Error(`版の形が読めません: ${version}（例: 0.3、v0.3）`);
  return `v${match[1]}.${Number(match[2]) + 1}-dev`;
}

/** 本文のリンクが書かれている base（公開 URL の `/katachi`）。本文はこの前提で書く。 */
export const writtenBase = '/katachi';

/**
 * このビルドの base。`KATACHI_DOCS_BASE` で差し替える（開発版のビルドは `/katachi/dev`）。
 * `writtenBase` と違うときは src/lib/satteri-rebase-links.mjs が本文の絶対リンクを付け替える。
 */
export const base = normalizeBase(env.KATACHI_DOCS_BASE ?? writtenBase);

/** @type {{ label: string }} */
export const currentVersion = {
  label: env.KATACHI_DOCS_VERSION_LABEL || devLabelAfter(archivedVersions[0].slug),
};

/**
 * 版の切り替えに足す開発版。リリースのビルドで `KATACHI_DOCS_DEV_BASE` が渡されたときだけある。
 * ラベルはこのビルドの現行の版（`v0.3`）の次（`v0.4-dev`）。
 * @type {{ label: string; base: string } | undefined}
 */
export const devVersion = env.KATACHI_DOCS_DEV_BASE
  ? { label: devLabelAfter(currentVersion.label), base: normalizeBase(env.KATACHI_DOCS_DEV_BASE) }
  : undefined;

/**
 * public/ の直下（と public/ja/ の直下）で、ページから張られるディレクトリ。public/ の下は版を持たない。
 * 古い版のページからここへのリンクは、版の区切りを外して最新のものへ向ける（src/lib/version-links.ts）。
 */
export const publicSections = ['api-docs', 'install', 'slides'];

/** 先頭に `/` を付け、末尾の `/` を外す（`/katachi/dev/` → `/katachi/dev`）。 */
function normalizeBase(value) {
  const trimmed = `/${value}`.replace(/^\/+/, '/').replace(/\/+$/, '');
  return trimmed === '' ? '/' : trimmed;
}
