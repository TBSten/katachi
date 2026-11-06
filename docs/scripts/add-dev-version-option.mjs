#!/usr/bin/env node
// root（最新のリリースタグのビルド）の版の切り替えに、開発版（/katachi/dev/）の選択肢を後から足す。
// 使い方: node scripts/add-dev-version-option.mjs <合成したサイトのフォルダ> --dev-base /katachi/dev --release-label v0.3
//
// root のビルドでは docs.yml が KATACHI_DOCS_DEV_BASE を渡し、VersionSelect.astro が末尾に開発版を足す。
// でもその仕組みを持たない古いタグ（v0.3.0 以前）の docs はこの環境変数を無視するので、root から開発版へ行けない。
// そこで docs.yml の assemble が、合成したあとの root の HTML に同じ選択肢を差し込む。
//
// - 行く先は VersionSelect.astro と同じく「開発版の同じ slug のページ」（版の区切り `0.2/` は外す）
// - 版の切り替えがすでに開発版の選択肢を持っているページ（v0.4.0 以降のタグ）は触らない。
//   root が v0.4.0 以降になったら何もしなくなるので、このスクリプトと docs.yml の手順は消してよい
// - `<dev-base>` の下（合成した開発版そのもの）は見ない
import { readdir, readFile, writeFile } from 'node:fs/promises';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { devLabelAfter } from '../src/lib/versions.mjs';

/** 版の区切り（`0.2`）。古いタグの写しの slug はこのスクリプトから分からないので、形で見分ける。 */
const VERSION_SEGMENT = /^\d+\.\d+$/;
/** root 以外のロケールの区切り。astro.config.mjs の locales と合わせる。 */
const LOCALES = ['ja'];

/**
 * サイトのフォルダからの相対パス（`ja/0.2/guides/role/index.html`）のページに対応する、開発版のページの絶対パス。
 * `index.html` でないページ（`404.html`）は開発版のトップへ送る。
 * @param {string} file `/` 区切りの相対パス
 * @param {string} devBase 開発版の base（`/katachi/dev`）
 */
export function devPagePath(file, devBase) {
	const segments = file.split('/');
	if (segments.pop() !== 'index.html') return `${devBase}/`;
	const locale = LOCALES.includes(segments[0]) ? segments.shift() : undefined;
	if (segments[0] && VERSION_SEGMENT.test(segments[0])) segments.shift();
	return `${devBase}/${[locale, ...segments].filter(Boolean).map((segment) => `${segment}/`).join('')}`;
}

const VERSION_SELECT = /(<starlight-version-select\b[\s\S]*?<select\b[^>]*>)([\s\S]*?)(<\/select>)/g;

/**
 * HTML の版の切り替え（`<starlight-version-select>` の中の `<select>`）の末尾に、開発版の選択肢を足す。
 * @param {string} html
 * @param {{ label: string; value: string }} option
 * @returns {{ html: string; status: 'added' | 'present' | 'none' }}
 *   added = 足した / present = もう持っていた（触らない）/ none = 版の切り替えが無いページ
 */
export function addDevVersionOption(html, { label, value }) {
	let status = /** @type {'added' | 'present' | 'none'} */ ('none');
	const result = html.replace(VERSION_SELECT, (whole, open, options, close) => {
		if (options.includes(`>${escapeHtml(label)}</option>`)) {
			if (status === 'none') status = 'present';
			return whole;
		}
		status = 'added';
		// 見た目を揃えるため、Astro が付けた scoped class（astro-xxxx）を既存の選択肢から写す。
		const className = /<option\b[^>]*\sclass="([^"]*)"/.exec(options)?.[1];
		const classAttr = className ? ` class="${className}"` : '';
		return `${open}${options}<option value="${escapeHtml(value)}"${classAttr}>${escapeHtml(label)}</option>${close}`;
	});
	return { html: result, status };
}

/** @param {string} text */
function escapeHtml(text) {
	return text.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
}

/**
 * `siteDir` の下の HTML（`skipDir` の下を除く）を相対パスで返す。
 * @param {string} siteDir
 * @param {string} skipDir `siteDir` からの相対パス（`dev`）
 */
async function htmlFiles(siteDir, skipDir) {
	const entries = await readdir(siteDir, { recursive: true, withFileTypes: true });
	return entries
		.filter((entry) => entry.isFile() && entry.name.endsWith('.html'))
		.map((entry) => relative(siteDir, join(entry.parentPath, entry.name)).split(sep).join('/'))
		.filter((file) => !(file === skipDir || file.startsWith(`${skipDir}/`)))
		.sort();
}

/** @param {string[]} argv */
function parseArgs(argv) {
	const [siteDir, ...rest] = argv;
	const options = new Map();
	for (let i = 0; i < rest.length; i += 2) options.set(rest[i], rest[i + 1]);
	const devBase = options.get('--dev-base');
	const releaseLabel = options.get('--release-label');
	if (!siteDir || !devBase || !releaseLabel) {
		throw new Error(
			'使い方: add-dev-version-option.mjs <サイトのフォルダ> --dev-base /katachi/dev --release-label v0.3',
		);
	}
	return { siteDir, devBase: devBase.replace(/\/+$/, ''), releaseLabel };
}

async function main() {
	const { siteDir, devBase, releaseLabel } = parseArgs(process.argv.slice(2));
	if (!/^v?\d+\.\d+/.test(releaseLabel)) {
		// workflow_dispatch で release_ref にブランチ名を渡したときなど。開発版のラベルが決まらないので足さない。
		console.log(`root のラベル ${releaseLabel} が版の形でないので、開発版の選択肢は足さない`);
		return;
	}
	const label = devLabelAfter(releaseLabel);
	// base `/katachi/dev` の開発版は、合成したサイトの `dev/` の下にある（base の最後の区切り）。
	const skipDir = devBase.split('/').filter(Boolean).at(-1) ?? '';
	const counts = { added: 0, present: 0, none: 0 };
	for (const file of await htmlFiles(siteDir, skipDir)) {
		const path = join(siteDir, file);
		const { html, status } = addDevVersionOption(await readFile(path, 'utf8'), {
			label,
			value: devPagePath(file, devBase),
		});
		counts[status] += 1;
		if (status === 'added') await writeFile(path, html);
	}
	console.log(
		`${label} の選択肢: 足した ${counts.added} ページ / もう持っていた ${counts.present} ページ / 版の切り替えが無い ${counts.none} ページ`,
	);
	if (counts.added === 0 && counts.present === 0) {
		throw new Error(
			`${siteDir} に版の切り替え（<starlight-version-select> の <select>）を持つページが 1 つもありません。` +
				'VersionSelect.astro の HTML の形が変わったなら、このスクリプトの VERSION_SELECT を合わせてください',
		);
	}
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
	main().catch((error) => {
		console.error(error instanceof Error ? error.message : error);
		process.exit(1);
	});
}
