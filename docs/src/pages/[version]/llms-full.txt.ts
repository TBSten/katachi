import type { APIRoute, GetStaticPaths } from 'astro';
// generator は pnpm の patch（patches/starlight-llms-txt@0.12.0.patch）で export している。
import { generateLlmsTxt } from 'starlight-llms-txt/generator';
import { archivedVersions } from '../../lib/versions.mjs';

// 凍結した版ごとの llms-full.txt（/katachi/0.3/llms-full.txt など）。
// ルートの /llms-full.txt は今の版だけにしている（astro.config.mjs の starlightLlmsTxt の `exclude`）ので、
// 古い版の全文はここから渡す。中身は凍結した版の写し（src/content/docs/<slug>/）の英語のページ。

export const prerender = true;

export const getStaticPaths = (() =>
	archivedVersions.map(({ slug, label }) => ({ params: { version: slug }, props: { slug, label } }))) satisfies GetStaticPaths;

export const GET: APIRoute<{ slug: string; label: string }> = async (context) => {
	const { slug, label } = context.props;
	const body = await generateLlmsTxt(context, {
		minify: false,
		description: `This is the full developer documentation for katachi ${label}, an archived version. The current version is at /llms-full.txt`,
		include: [`${slug}/**`],
	});
	return new Response(body);
};
