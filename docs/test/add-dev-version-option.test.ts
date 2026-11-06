import { describe, expect, it } from 'vitest';
import { addDevVersionOption, devPagePath } from '../scripts/add-dev-version-option.mjs';

// v0.3.0 タグの docs が出す版の切り替え（dist/ja/get-started/first-architecture/index.html から抜き出し、アイコンの svg は省略）。
const v030Select =
	'<starlight-version-select class="astro-jcjov46q"><label class="astro-jcjov46q"><span class="sr-only astro-jcjov46q">版を選ぶ</span>' +
	'<select autocomplete="off" class="astro-jcjov46q">' +
	'<option selected value="/katachi/ja/get-started/first-architecture/" class="astro-jcjov46q">v0.3</option>' +
	'<option value="/katachi/ja/0.2/get-started/first-architecture/" class="astro-jcjov46q">v0.2</option>' +
	'</select></label></starlight-version-select>';

const devOption = { label: 'v0.4-dev', value: '/katachi/dev/ja/get-started/first-architecture/' };

describe('devPagePath', () => {
	it('同じ slug の開発版のページへ送る', () => {
		expect(devPagePath('ja/get-started/first-architecture/index.html', '/katachi/dev')).toBe(
			'/katachi/dev/ja/get-started/first-architecture/',
		);
		expect(devPagePath('guides/role/index.html', '/katachi/dev')).toBe('/katachi/dev/guides/role/');
	});

	it('古い版の写しのページは、版の区切りを外す', () => {
		expect(devPagePath('ja/0.2/guides/role/index.html', '/katachi/dev')).toBe('/katachi/dev/ja/guides/role/');
		expect(devPagePath('0.2/index.html', '/katachi/dev')).toBe('/katachi/dev/');
	});

	it('トップページと index.html でないページは開発版のトップ', () => {
		expect(devPagePath('index.html', '/katachi/dev')).toBe('/katachi/dev/');
		expect(devPagePath('ja/index.html', '/katachi/dev')).toBe('/katachi/dev/ja/');
		expect(devPagePath('404.html', '/katachi/dev')).toBe('/katachi/dev/');
	});
});

describe('addDevVersionOption', () => {
	it('版の切り替えの末尾に、既存の選択肢と同じ class で足す', () => {
		const { html, status } = addDevVersionOption(`<header>${v030Select}</header>`, devOption);
		expect(status).toBe('added');
		expect(html).toContain(
			'>v0.2</option><option value="/katachi/dev/ja/get-started/first-architecture/" class="astro-jcjov46q">v0.4-dev</option></select>',
		);
	});

	it('ページにある版の切り替えすべて（ヘッダとサイドバー）に足す', () => {
		const { html } = addDevVersionOption(`${v030Select}<select id="lang"><option>日本語</option></select>${v030Select}`, devOption);
		expect(html.match(/>v0\.4-dev<\/option>/g)).toHaveLength(2);
		// 版の切り替えでない select（言語の切り替えなど）には足さない
		expect(html).toContain('<select id="lang"><option>日本語</option></select>');
	});

	it('もう開発版の選択肢を持っている版の切り替え（v0.4.0 以降のタグ）は触らない', () => {
		const once = addDevVersionOption(v030Select, devOption).html;
		expect(addDevVersionOption(once, devOption)).toEqual({ html: once, status: 'present' });
	});

	it('版の切り替えが無いページはそのまま', () => {
		const page = '<html><body><select><option>x</option></select></body></html>';
		expect(addDevVersionOption(page, devOption)).toEqual({ html: page, status: 'none' });
	});
});
