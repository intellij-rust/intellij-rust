/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.selfParameter
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.types.ty.TyFunction
import org.rust.lang.core.types.type

class CallInfo private constructor(
    val selfParameter: String?,
    val parameters: List<Parameter>
) {
    class Parameter(val pattern: String, val type: String)

    companion object {
        fun resolve(call: RsCallExpr): CallInfo? {
            val fn = (call.expr as? RsPathExpr)?.path?.reference?.resolve() ?: return null
            if (fn is RsFunction) return CallInfo(fn)

            val ty = call.expr.type
            if (ty is TyFunction) return CallInfo(ty)
            return null
        }

        fun resolve(methodCall: RsMethodCall): CallInfo? {
            return (methodCall.reference.resolve() as? RsFunction)?.let { CallInfo(it) }
        }
    }

    private constructor(fn: RsFunction) : this(
        fn.selfParameter?.text,
        fn.valueParameters.map { Parameter(it.pat?.text ?: "_", it.typeReference?.text ?: "?") }
    )

    private constructor(fn: TyFunction) : this(
        null,
        fn.paramTypes.map { Parameter("_", it.toString()) }
    )
}
