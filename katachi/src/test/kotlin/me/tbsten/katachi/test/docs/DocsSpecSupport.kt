package me.tbsten.katachi.test.docs

import me.tbsten.katachi.docs.roleReferenceDocuments
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.test.fs.ForbiddenFileSystem

/**
 * The pages this definition produces, built against a tree that refuses to be read.
 *
 * [ForbiddenFileSystem] rather than a fake one on purpose: every spec below then also asserts,
 * without saying so, that building documentation reads nothing — a fake tree would answer
 * happily and the specs would stay green if that stopped being true.
 */
internal fun Architecture.documents(): Map<String, String> =
    process(ForbiddenFileSystem) { context -> roleReferenceDocuments(context) }

/** One page of [documents], which fails the spec rather than returning null when it is absent. */
internal fun Architecture.page(path: String): String = documents().getValue(path)
