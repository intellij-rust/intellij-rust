/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.knownItems

val RsPat.isIrrefutable: Boolean
    get() = when (this) {
        is RsPatTupleStruct -> patList.all { it.isIrrefutable }
        is RsPatSlice -> patList.all { it.isIrrefutable }
        is RsPatTup -> patList.all { it.isIrrefutable }
        is RsPatBox -> pat.isIrrefutable
        is RsPatRef -> pat.isIrrefutable
        is RsPatStruct -> {
            val canBeIrrefutable = when (val item = path.reference.resolve()) {
                is RsStructItem -> true
                is RsEnumVariant -> item.parentEnum.enumBody?.enumVariantList?.size == 1
                else -> false
            }
            canBeIrrefutable && patFieldList.all { it.pat?.isIrrefutable ?: (it.patBinding != null) }
        }
        is RsPatConst, is RsPatRange -> false
        else -> true
    }

fun RsPat.skipUnnecessaryTupDown(): RsPat {
    var pat = this
    while (pat is RsPatTup) {
        pat = pat.patList.singleOrNull() ?: return pat
    }
    return pat
}

fun matchStdOptionOrResult(item: RsEnumItem?, patterns: List<RsPat>, allVariants: Boolean = false): Boolean {
    if (patterns.isEmpty() || item?.isStdOptionOrResult != true) return false
    val boolOp = if (allVariants) Boolean::and else Boolean::or
    return patterns.map { it.skipUnnecessaryTupDown().text.substringBefore('(') }.let {
        if (item == item!!.knownItems.Option) {
            boolOp("Some" in it, "None" in it)
        } else {
            boolOp("Ok" in it, "Err" in it)
        }
    }
}
