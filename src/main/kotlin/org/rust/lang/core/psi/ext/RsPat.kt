/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*

val RsPat.isIrrefutable: Boolean
    get() = when (this) {
        is RsPatTupleStruct -> patList.all { it.isIrrefutable }
        is RsPatSlice -> patList.all { it.isIrrefutable }
        is RsPatTup -> patList.all { it.isIrrefutable }
        is RsPatBox -> pat.isIrrefutable
        is RsPatRef -> pat.isIrrefutable
        is RsPatStruct -> patFieldList.all { it.pat?.isIrrefutable ?: it.patBinding != null }
        is RsPatConst, is RsPatRange -> false
        else -> true
    }
