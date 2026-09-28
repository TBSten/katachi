import type { APIRoute, GetStaticPaths } from 'astro';
import { getCollection } from 'astro:content';

// Strip MDX scaffolding only outside fenced code examples.
function markdownBody(body: string): string {
	let fence: string | undefined;
	let comment = false;
	let importing = false;
	return body.split('\n').map((line) => {
		if (fence) {
			const closing = line.match(/^\s{0,3}(`{3,}|~{3,})\s*$/)?.[1];
			if (closing?.[0] === fence[0] && closing.length >= fence.length) fence = undefined;
			return line;
		}
		if (!comment && !importing) {
			const opening = line.match(/^\s*(`{3,}|~{3,})/)?.[1];
			if (opening) { fence = opening; return line; }
		}
		let clean = '';
		let rest = line;
		while (rest) {
			if (comment) {
				const end = rest.indexOf('*/}');
				if (end < 0) break;
				rest = rest.slice(end + 3);
				comment = false;
			} else {
				const start = rest.indexOf('{/*');
				if (start < 0) { clean += rest; break; }
				clean += rest.slice(0, start);
				rest = rest.slice(start + 3);
				comment = true;
			}
		}
		if (/^import\s/.test(clean) || importing) {
			importing = !/["'][^"']+["']\s*;?\s*$/.test(clean);
			return '';
		}
		return clean;
	}).join('\n');
}

export const getStaticPaths = (async () => {
	const entries = await getCollection('docs');
	return entries.map((entry) => ({
		params: { slug: entry.id },
		props: { title: entry.data.title, body: entry.body ?? '' },
	}));
}) satisfies GetStaticPaths;

export const GET: APIRoute = ({ props }) => new Response(
	`# ${props.title}\n\n${markdownBody(props.body)}`,
	{ headers: { 'Content-Type': 'text/markdown; charset=utf-8' } },
);
