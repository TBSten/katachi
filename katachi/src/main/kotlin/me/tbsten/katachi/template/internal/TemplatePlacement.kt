package me.tbsten.katachi.template.internal

// Superseded by TemplateGeneration.kt: a `.template { }` now attaches to one file declaration
// directly (`DeclaredTemplate.entries`), so there is no candidate search over a role's file
// patterns left to do -- see the design draft's section 4. Left as an empty file rather than
// removed: this harness's permission boundary refuses a raw file deletion (see
// .local/tmp/template-per-file/notes-A.md for the same call made by 担当 A on the test files it
// could not delete either).
