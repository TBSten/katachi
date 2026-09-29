/**
 * 外部リンクの OGP 画像（og:image）をビルド時に取得する。LinkCard の右に出す画像の元。
 *
 * - **失敗してもビルドは落とさない。** タイムアウト・HTTP エラー・画像なしはすべて `null` を返し、
 *   カードは画像なしの今までの見た目になる。
 * - **取得結果はキャッシュする。** 同じビルドの中ではメモリ、次のビルド以降は
 *   `node_modules/.cache/katachi-link-og/cache.json`（成功は 7 日、失敗は 1 日）。
 * - **画像は取り込まない（ホットリンク）。** URL だけを持ち、画像自体は読者のブラウザが取りに行く。
 *   理由は notes に。取れない画像は `<img onerror>` で消える。
 * - 内部リンク（`/`・`./`・`../` で始まる）は対象外。サイト共通の og.png しか無く、全カードに
 *   同じ画像が並ぶだけなので。
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

// ビルド後のチャンクでは import.meta.url が dist 側を指すので、cwd（docs/）基準にする。
const CACHE_FILE = resolve(process.cwd(), 'node_modules/.cache/katachi-link-og/cache.json');
const TIMEOUT_MS = 8000;
const OK_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const NG_TTL_MS = 24 * 60 * 60 * 1000;
const MAX_HTML_BYTES = 512 * 1024;

type Entry = { image: string | null; at: number };

let disk: Record<string, Entry> | undefined;
const pending = new Map<string, Promise<string | null>>();

const loadDisk = () => {
	if (disk) return disk;
	try {
		disk = JSON.parse(readFileSync(CACHE_FILE, 'utf8'));
	} catch {
		disk = {};
	}
	return disk!;
};

const saveDisk = () => {
	try {
		mkdirSync(dirname(CACHE_FILE), { recursive: true });
		writeFileSync(CACHE_FILE, JSON.stringify(disk));
	} catch {
		// キャッシュが書けなくても困らない。
	}
};

export const isExternal = (href: string | undefined): href is string =>
	!!href && /^https?:\/\//i.test(href);

const attr = (tag: string, name: string) =>
	new RegExp(`${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)')`, 'i').exec(tag)?.slice(1).find(Boolean);

const decode = (s: string) =>
	s.replace(/&amp;/g, '&').replace(/&#x2F;/gi, '/').replace(/&quot;/g, '"').replace(/&#39;/g, "'");

const extract = (html: string, base: string): string | null => {
	const wanted = ['og:image:secure_url', 'og:image:url', 'og:image', 'twitter:image', 'twitter:image:src'];
	const found = new Map<string, string>();
	for (const m of html.matchAll(/<meta\b[^>]*>/gi)) {
		const key = attr(m[0], 'property') ?? attr(m[0], 'name');
		const content = attr(m[0], 'content');
		if (key && content && wanted.includes(key.toLowerCase()) && !found.has(key.toLowerCase())) {
			found.set(key.toLowerCase(), content);
		}
	}
	for (const key of wanted) {
		const raw = found.get(key);
		if (!raw) continue;
		try {
			const url = new URL(decode(raw.trim()), base);
			if (url.protocol === 'https:') return url.href;
		} catch {
			// 次の候補へ
		}
	}
	return null;
};

const fetchImage = async (href: string): Promise<string | null> => {
	try {
		const res = await fetch(href, {
			signal: AbortSignal.timeout(TIMEOUT_MS),
			redirect: 'follow',
			headers: { 'user-agent': 'Mozilla/5.0 (compatible; katachi-docs-build)', accept: 'text/html' },
		});
		if (!res.ok || !(res.headers.get('content-type') ?? '').includes('html')) return null;
		const html = (await res.text()).slice(0, MAX_HTML_BYTES);
		return extract(html, res.url || href);
	} catch (e) {
		console.warn(`[link-og-image] ${href}: ${(e as Error).message} (画像なしで続行)`);
		return null;
	}
};

/** `href` の OGP 画像の絶対 https URL。無い・取れないときは `null`。 */
export const getLinkOgImage = (href: string | undefined): Promise<string | null> => {
	if (!isExternal(href)) return Promise.resolve(null);
	const key = href.replace(/#.*$/, '');
	const cached = loadDisk()[key];
	if (cached && Date.now() - cached.at < (cached.image ? OK_TTL_MS : NG_TTL_MS)) {
		return Promise.resolve(cached.image);
	}
	let p = pending.get(key);
	if (!p) {
		p = fetchImage(key).then((image) => {
			loadDisk()[key] = { image, at: Date.now() };
			saveDisk();
			return image;
		});
		pending.set(key, p);
	}
	return p;
};
