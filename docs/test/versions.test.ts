import { describe, expect, it } from 'vitest';
import { archivedVersions, currentVersion, devLabelAfter } from '../src/lib/versions.mjs';
import { rebase, satteriRebaseLinks } from '../src/lib/satteri-rebase-links.mjs';

describe('devLabelAfter', () => {
	it('凍結した版の slug から、次の minor の開発版のラベルを作る', () => {
		expect(devLabelAfter('0.3')).toBe('v0.4-dev');
		expect(devLabelAfter('0.9')).toBe('v0.10-dev');
	});

	it('ラベル（v 付き・patch 付き）からも作れる', () => {
		expect(devLabelAfter('v0.3')).toBe('v0.4-dev');
		expect(devLabelAfter('v1.2.3')).toBe('v1.3-dev');
	});

	it('版の形でなければ落ちる', () => {
		expect(() => devLabelAfter('main')).toThrow('版の形が読めません');
	});
});

describe('currentVersion', () => {
	it('省略時は、凍結した版の先頭の次の開発版', () => {
		// KATACHI_DOCS_VERSION_LABEL が無い前提（docs.yml のリリースのビルドだけが渡す）。
		expect(currentVersion.label).toBe(devLabelAfter(archivedVersions[0].slug));
	});
});

describe('rebase（satteri-rebase-links）', () => {
	it('base が /katachi のままなら何も変えない（KATACHI_DOCS_BASE が無い前提）', () => {
		expect(rebase('/katachi/guides/role/')).toBe('/katachi/guides/role/');
		expect(rebase('https://example.com/katachi/')).toBe('https://example.com/katachi/');
		expect(satteriRebaseLinks()).toBe(false);
	});
});
