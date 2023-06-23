/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.types.mergeFlags

abstract class TyFunctionBase(
    val fnSig: FnSig
) : Ty(mergeFlags(fnSig.paramTypes) or fnSig.retType.flags) {
    val paramTypes: List<Ty>
        get() = fnSig.paramTypes

    val retType: Ty
        get() = fnSig.retType

    val unsafety: Unsafety
        get() = fnSig.unsafety
}
