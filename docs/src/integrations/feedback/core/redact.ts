// 秘密情報の伏せ字。feedback-mode skill の redact と同じパターン。
// 記録の文全体に 1 回だけ掛ける（コピーと保存で文を変えないため、組み立ての 1 か所で）。

export function redact(text: string): string {
	return String(text)
		.replace(/\b(Bearer)\s+[A-Za-z0-9._~+/=-]{8,}/gi, '$1 ***')
		.replace(/\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{4,}/g, '***')
		.replace(/\b(Cookie|Set-Cookie)\s*:\s*[^\n]+/gi, '$1: ***')
		.replace(
			/\b(_csrf|csrf[_-]?token|x-csrf-token|access_token|refresh_token|id_token|client_secret|authorization|token|password|passwd|secret|api[_-]?key)(["']?\s*[:=]\s*["']?)[^\s"'&,;}]{6,}/gi,
			'$1$2***',
		)
		.replace(/\b(?:sk-[A-Za-z0-9_-]{16,}|gh[pous]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})/g, '***')
}
