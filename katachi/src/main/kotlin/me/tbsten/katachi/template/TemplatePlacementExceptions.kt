package me.tbsten.katachi.template

// Superseded: a `.template { }` now attaches to one file declaration, whose path is already
// known before generation runs, so there is no "collect candidates, refuse if ambiguous or
// none" placement stage left to raise an exception about -- see the design draft's section 4,
// "今の決まりとの関係". `KatachiNoTemplatePlacementException`, `KatachiWildcardTemplatePlacementException`
// and `KatachiAmbiguousTemplatePlacementException` are removed; the one placement failure that
// can still happen without being a declaration-time error (a filled path not matching its own
// pattern) is `KatachiTemplatePathMismatchException`, in TemplateFileNameExceptions.kt. Left as
// an empty file rather than removed: this harness's permission boundary refuses a raw file
// deletion (see .local/tmp/template-per-file/notes-A.md for the same call made by 担当 A on the
// test files it could not delete either).
