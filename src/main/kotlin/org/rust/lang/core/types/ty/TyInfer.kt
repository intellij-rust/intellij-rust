/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.infer.Node
import org.rust.lang.core.types.infer.NodeOrValue
import org.rust.lang.core.types.infer.VarValue

sealed class TyInfer : Ty {
    class TyVar(val origin: TyTypeParameter, override var parent: NodeOrValue = VarValue(null, 0)) : TyInfer(), Node
    class IntVar(override var parent: NodeOrValue = VarValue(null, 0)) : TyInfer(), Node
    class FloatVar(override var parent: NodeOrValue = VarValue(null, 0)) : TyInfer(), Node

    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult =
        UnifyResult.fail

    override fun toString(): String = tyToString(this)
}
