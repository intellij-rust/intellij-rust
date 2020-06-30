/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.ref.deepResolve

val RsPat.isIrrefutable: Boolean
    get() = when (val pat = skipUnnecessaryTupDown()) {
        is RsPatSlice ->
            when (val nestedPat = pat.patList.singleOrNull()) {
                is RsPatRest -> true
                is RsPatIdent -> nestedPat.pat is RsPatRest
                else -> false
            }
        is RsPatTup ->
            pat.patList.all { it.isIrrefutable }
        is RsPatBox ->
            pat.pat.isIrrefutable
        is RsPatRef ->
            pat.pat.isIrrefutable
        is RsPatStruct ->
            pat.path.isIrrefutable && pat.patFieldList.all {
                it.patFieldFull?.pat?.isIrrefutable ?: (it.patBinding != null)
            }
        is RsPatTupleStruct ->
            pat.path.isIrrefutable && pat.patList.all { it.isIrrefutable }
        is RsPatIdent ->
            pat.patBinding.isIrrefutable
        is RsPatConst ->
            (pat.expr as? RsPathExpr)?.path?.isIrrefutable ?: false
        is RsPatRange ->
            false
        // FIXME: support it
        is RsOrPat ->
            false
        else ->
            true
    }

private val RsPath.isIrrefutable: Boolean
    get() = when (val item = reference?.deepResolve()) {
        is RsStructItem -> true
        is RsEnumVariant -> item.parentEnum.enumBody?.enumVariantList?.size == 1
        else -> false
    }

private val RsPatBinding.isIrrefutable: Boolean
    get() = when (reference.resolve()) {
        is RsStructItem, is RsEnumVariant, is RsConstant -> false
        else -> true
    }

fun RsPat.skipUnnecessaryTupDown(): RsPat {
    var pat = this
    while (pat is RsPatTup) {
        pat = pat.patList.singleOrNull() ?: return pat
    }
    return pat
}
