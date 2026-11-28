import { describe, expect, it } from 'vitest';
import { archivedVersions, currentIsDev, currentVersion, latestReleasedVersion } from '../src/lib/versions.mjs';
import { showsDevNotice, versionNoticeStrings, versionNoticeText } from '../src/lib/version-notice';

describe('showsDevNotice（版の注意を出すページ）', () => {
	const dev = { currentIsDev: true, archivedVersion: undefined };

	it('開発中の版のページには出す', () => {
		expect(showsDevNotice({ ...dev, id: 'guides/role' })).toBe(true);
		expect(showsDevNotice({ ...dev, id: 'ja/guides/role' })).toBe(true);
	});

	it('リリースのビルド（いま書いている版がリリース済み）のページには出さない', () => {
		expect(showsDevNotice({ id: 'guides/role', currentIsDev: false, archivedVersion: undefined })).toBe(false);
	});

	it('凍結した写し（リリース済みの版）のページには、開発版のビルドの中でも出さない', () => {
		expect(showsDevNotice({ id: '0.3/guides/role', currentIsDev: true, archivedVersion: '0.3' })).toBe(false);
		expect(showsDevNotice({ id: 'ja/0.3/guides/role', currentIsDev: false, archivedVersion: '0.3' })).toBe(false);
	});

	it('404 と、docs のページでないもの（id が無い）には出さない', () => {
		expect(showsDevNotice({ ...dev, id: '404' })).toBe(false);
		expect(showsDevNotice({ ...dev, id: 'ja/404' })).toBe(false);
		expect(showsDevNotice({ ...dev, id: undefined })).toBe(false);
		expect(showsDevNotice({ ...dev, id: '' })).toBe(false);
	});
});

describe('versionNoticeText（文言）', () => {
	it('日本語は原本、それ以外は英語', () => {
		expect(versionNoticeText('ja')).toBe(versionNoticeStrings.ja);
		expect(versionNoticeText('en')).toBe(versionNoticeStrings.en);
		expect(versionNoticeText(undefined)).toBe(versionNoticeStrings.en);
	});

	it('リンクを挟んで 1 つの文になる', () => {
		const sentence = (lang: string) => {
			const text = versionNoticeText(lang);
			return `${text.dev}${text.joiner}${text.latestBefore}v0.4${text.latestAfter}`;
		};
		expect(sentence('ja')).toBe('このページは開発中バージョンの内容です。最新のドキュメントは v0.4 を参照してください。');
		expect(sentence('en')).toBe('This page is for the version in development. See v0.4 for the latest documentation.');
	});

	it('英語の文言に全角の文字が残っていない', () => {
		for (const value of Object.values(versionNoticeStrings.en)) expect(value).toMatch(/^[\x20-\x7e]*$/);
	});
});

describe('versions.mjs（注意が読む値）', () => {
	it('最新のリリース済みの版は、凍結した版の先頭', () => {
		expect(latestReleasedVersion).toBe(archivedVersions[0]);
	});

	it('ラベルを渡さないビルドは開発中（KATACHI_DOCS_VERSION_LABEL が無い前提）', () => {
		expect(currentVersion.label).toMatch(/-dev$/);
		expect(currentIsDev).toBe(true);
	});
});
