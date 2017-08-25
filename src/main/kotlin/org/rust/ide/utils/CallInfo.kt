/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyFunction
import org.rust.lang.core.types.type

class CallInfo private constructor(
    val methodName: String?,
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
        fn.name,
        fn.selfParameter?.let { self ->
            buildString {
                if (self.isRef) append("&")
                if (self.mutability.isMut) append("mut ")
                append("self")
            }
        },
        fn.valueParameters.map { Parameter(it.patText ?: "_", it.typeReferenceText ?: "?") }
    )

    private constructor(fn: TyFunction) : this(
        null,
        null,
        fn.paramTypes.map { Parameter("_", it.toString()) }
    )
}
