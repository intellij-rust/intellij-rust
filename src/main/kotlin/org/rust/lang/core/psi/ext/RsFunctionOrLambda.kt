/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsLambdaExpr
import org.rust.lang.core.psi.RsRetType
import org.rust.lang.core.psi.RsValueParameterList

/**
 * [org.rust.lang.core.psi.RsFunction] or [org.rust.lang.core.psi.RsLambdaExpr]
 */
interface RsFunctionOrLambda : RsOuterAttributeOwner {
    val valueParameterList: RsValueParameterList?
    val retType: RsRetType?
}

val RsFunctionOrLambda.isAsync: Boolean
    get() = when (this) {
        is RsFunction -> isAsync
        is RsLambdaExpr -> isAsync
        else -> error("unreachable")
    }
