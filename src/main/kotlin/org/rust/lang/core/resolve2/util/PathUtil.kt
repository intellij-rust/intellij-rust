/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.util

import org.rust.lang.core.stubs.*

fun interface RsLeafUseSpeckConsumer {
    fun consume(usePath: Array<String>, alias: String?, isStarImport: Boolean, offsetInExpansion: Int)
}

fun RsUseItemStub.forEachLeafSpeck(consumer: RsLeafUseSpeckConsumer) {
    val rootUseSpeck = findChildStubByType(RsUseSpeckStub.Type) ?: return
    val segments = arrayListOf<String>()
    rootUseSpeck.forEachLeafSpeck(consumer, segments, isRootSpeck = true, basePath = null)
}

private fun RsUseSpeckStub.forEachLeafSpeck(
    consumer: RsLeafUseSpeckConsumer,
    segments: ArrayList<String>,
    isRootSpeck: Boolean,
    basePath: RsPathStub?
) {
    val path = path
    val useGroup = useGroup
    val isStarImport = isStarImport

    if (path == null && isRootSpeck) {
        // e.g. `use ::*;` and `use ::{aaa, bbb};`
        if (hasColonColon) segments += "crate"
        // We ignore `use *;` - https://github.com/sfackler/rust-openssl/blob/0a0da84f939090f72980c77f40199fc76245d289/openssl-sys/src/asn1.rs#L3
        // Note that `use ::*;` is correct in 2015 edition
        if (!hasColonColon && useGroup == null) return
    }

    val numberSegments = segments.size
    if (path != null) {
        if (!addPathSegments(path, segments)) return
    }

    val newBasePath = basePath ?: path
    if (useGroup == null) {
        if (!isStarImport && segments.size > 1 && segments.last() == "self") segments.removeAt(segments.lastIndex)
        val alias = if (isStarImport) "_" else alias?.name
        consumer.consume(segments.toTypedArray(), alias, isStarImport, newBasePath?.startOffset ?: -1)
    } else {
        for (childSpeck in useGroup.childrenStubs) {
            (childSpeck as RsUseSpeckStub).forEachLeafSpeck(consumer, segments, isRootSpeck = false, newBasePath)
        }
    }

    while (segments.size > numberSegments) {
        segments.removeAt(segments.lastIndex)
    }
}

/** return false if path is incomplete (has empty segments), e.g. `use std::;` */
private fun addPathSegments(path: RsPathStub, segments: ArrayList<String>): Boolean {
    val subpath = path.path
    if (subpath != null) {
        if (!addPathSegments(subpath, segments)) return false
    } else if (path.hasColonColon) {
        // absolute path: `::foo::bar`
        //                 ~~~~~ this
        segments += ""
    }

    val segment = path.referenceName ?: return false
    segments += segment
    return true
}

fun RsMacroCallStub.getPathWithAdjustedDollarCrate(): Array<String>? {
    val segments = arrayListOf<String>()
    if (!addPathSegments(path, segments)) return null
    return segments.toTypedArray()
}

fun RsMetaItemStub.getPathWithAdjustedDollarCrate(): Array<String>? {
    val segments = arrayListOf<String>()

    val path = path ?: return null
    if (!addPathSegments(path, segments)) return null
    return segments.toTypedArray()
}

fun RsVisStub.getRestrictedPath(): Array<String>? {
    val path = visRestrictionPath ?: error("no visibility restriction")
    val segments = arrayListOf<String>()
    if (!addPathSegments(path, segments)) return null
    if (segments.first().let { it.isEmpty() || it == "crate" }) segments.removeAt(0)
    return segments.toTypedArray()
}
