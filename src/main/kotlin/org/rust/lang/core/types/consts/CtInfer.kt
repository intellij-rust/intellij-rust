/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.consts

import org.rust.lang.core.types.HAS_CT_INFER_MASK
import org.rust.lang.core.types.infer.Node
import org.rust.lang.core.types.infer.NodeOrValue
import org.rust.lang.core.types.infer.VarValue

class CtInferVar(
    val origin: Const? = null,
    override var parent: NodeOrValue = VarValue(null, 0)
) : Const(HAS_CT_INFER_MASK), Node

data class FreshCtInferVar(val id: Int) : Const()
