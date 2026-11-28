import { describe, expect, it } from 'vitest';
import {
	firstSlug,
	latestReleasedPath,
	pagePath,
	pageSlug,
	resolveVersionPath,
} from '../src/lib/version-destination';

const base = '/katachi';
const available = new Set([
	'/katachi/',
	'/katachi/guides/role/',
	'/katachi/guides/kt-file-content/',
	'/katachi/0.2/guides/role/',
	'/katachi/0.2/get-started/motivation/',
	'/katachi/ja/',
	'/katachi/ja/guides/role/',
	'/katachi/ja/0.2/guides/role/',
	'/katachi/ja/0.2/get-started/motivation/',
]);

describe('pageSlug', () => {
	it.each([
		['guides/role', undefined, 'guides/role'],
		['0.2/guides/role', undefined, 'guides/role'],
		['ja/guides/role', 'ja', 'guides/role'],
		['ja/0.2/guides/role', 'ja', 'guides/role'],
		['ja/0.2/roadmap', 'ja', 'roadmap'],
	])('%s は版と locale を外す', (id, locale, expected) => {
		expect(pageSlug(id, locale, ['0.2'])).toBe(expected);
	});
});

describe('resolveVersionPath', () => {
	const go = (slug: string, version: string | undefined, locale?: string) =>
		resolveVersionPath({ base, locale, version, slug }, available, 'get-started/motivation');

	it('両方の版にあるページは同じページへ', () => {
		expect(go('guides/role', '0.2')).toBe('/katachi/0.2/guides/role/');
		expect(go('guides/role', '0.2', 'ja')).toBe('/katachi/ja/0.2/guides/role/');
		expect(go('guides/role', undefined)).toBe('/katachi/guides/role/');
	});

	it('その版に無いページは、版のトップが無ければ最初のページへ', () => {
		expect(go('guides/kt-file-content', '0.2')).toBe('/katachi/0.2/get-started/motivation/');
		expect(go('', '0.2', 'ja')).toBe('/katachi/ja/0.2/get-started/motivation/');
	});

	it('最新に無いページは最新のトップへ', () => {
		expect(go('guides/gone', undefined)).toBe('/katachi/');
		expect(go('guides/gone', undefined, 'ja')).toBe('/katachi/ja/');
	});

	it('版のトップがあればそこへ', () => {
		const withTop = new Set([...available, '/katachi/0.2/']);
		expect(resolveVersionPath({ base, version: '0.2', slug: 'showcase' }, withTop, 'x')).toBe('/katachi/0.2/');
	});
});

describe('latestReleasedPath（開発中の版のページから、最新のリリース済みの版へ）', () => {
	const first = 'get-started/motivation';

	describe('手元のビルド（base がリリース済みの docs と同じ）は、ビルドの中の写しへ', () => {
		const go = (slug: string, locale?: string) =>
			latestReleasedPath({ base, releasedBase: base, locale, version: '0.2', slug }, available, first);

		it('写しにあるページは同じページへ', () => {
			expect(go('guides/role')).toBe('/katachi/0.2/guides/role/');
			expect(go('guides/role', 'ja')).toBe('/katachi/ja/0.2/guides/role/');
		});

		it('写しに無いページ（版を切ったあとに足したページ）は、写しの最初のページへ', () => {
			expect(go('guides/kt-file-content')).toBe('/katachi/0.2/get-started/motivation/');
		});
	});

	describe('公開サイトの開発版（base が /katachi/dev）は、リリース済みの docs の直下へ', () => {
		const devBase = '/katachi/dev';
		const devAvailable = new Set([...available].map((path) => path.replace(base, devBase)));
		const go = (slug: string, locale?: string) =>
			latestReleasedPath({ base: devBase, releasedBase: base, locale, version: '0.2', slug }, devAvailable, first);

		it('写しにあるページは、版の区切りの無い同じページへ', () => {
			expect(go('guides/role')).toBe('/katachi/guides/role/');
			expect(go('guides/role', 'ja')).toBe('/katachi/ja/guides/role/');
		});

		it('写しに無いページは、リリース済みの docs のトップへ', () => {
			expect(go('guides/kt-file-content')).toBe('/katachi/');
			expect(go('guides/kt-file-content', 'ja')).toBe('/katachi/ja/');
		});
	});
});

it('pagePath と firstSlug', () => {
	expect(pagePath({ base, locale: 'ja', version: '0.2', slug: '' })).toBe('/katachi/ja/0.2/');
	expect(firstSlug([{ items: [{ slug: 'a/b' }] }, { slug: 'c' }])).toBe('a/b');
	expect(firstSlug(undefined)).toBeUndefined();
});
