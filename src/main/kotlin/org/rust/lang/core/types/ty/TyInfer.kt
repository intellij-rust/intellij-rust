/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.types.HAS_TY_INFER_MASK
import org.rust.lang.core.types.infer.Node
import org.rust.lang.core.types.infer.NodeOrValue
import org.rust.lang.core.types.infer.VarValue

sealed class TyInfer : Ty(HAS_TY_INFER_MASK) {
    // Note these classes must NOT be `data` classes and must provide equality by identity
    class TyVar(
        val origin: Ty? = null,
        override var parent: NodeOrValue = VarValue(null, 0)
    ) : TyInfer(), Node

    class IntVar(override var parent: NodeOrValue = VarValue(null, 0)) : TyInfer(), Node
    class FloatVar(override var parent: NodeOrValue = VarValue(null, 0)) : TyInfer(), Node
}

/** Used for caching only */
sealed class FreshTyInfer : Ty() {
    data class TyVar(val id: Int) : FreshTyInfer()
    data class IntVar(val id: Int) : FreshTyInfer()
    data class FloatVar(val id: Int) : FreshTyInfer()
}
