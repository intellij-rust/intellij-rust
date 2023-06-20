/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.mir.asSpan
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.block
import org.rust.lang.core.psi.ext.normReturnType
import org.rust.lang.core.types.infer.typeOfValue
import org.rust.lang.core.types.type

data class Thir(
    val expr: ThirExpr,
    val params: List<ThirParam>,
) {
    companion object {
        fun from(constant: RsConstant): Thir {
            val body = constant.expr ?: error("Could not get expression of constant")
            val expr = MirrorContext(constant).mirrorExpr(body)
            return Thir(expr, emptyList())
        }

        fun from(function: RsFunction): Thir {
            val body = function.block ?: error("Could not get block of function")
            val expr = MirrorContext(function).mirrorBlock(body, function.normReturnType)
            val params = explicitParams(function)
            return Thir(expr, params)
        }

        private fun explicitParams(function: RsFunction): List<ThirParam> = buildList {
            val params = function.valueParameterList ?: error("Could not get function's parameters")
            val self = params.selfParameter
            if (self != null) {
                val selfKind = ImplicitSelfKind.from(self).takeIf { it.hasImplicitSelf }
                val tySpan = if (self.colon != null) {
                    self.typeReference?.asSpan ?: error("Could not get self parameter's type")
                } else {
                    null
                }
                val thirParam = ThirParam(
                    pat = ThirPat.from(self),
                    ty = self.typeOfValue,
                    tySpan = tySpan,
                    selfKind = selfKind,
                )
                add(thirParam)
            }
            params.valueParameterList.forEach { param ->
                val tySpan = param.typeReference?.asSpan ?: error("Could not get parameter's type")
                // TODO: in case of closures tySpan should be null
                val pat = param.pat ?: error("Could not extract pat from parameter")
                val thirParam = ThirParam(
                    pat = ThirPat.from(pat),
                    ty = pat.type,
                    tySpan = tySpan,
                    selfKind = null,
                )
                add(thirParam)
            }
        }
    }
}
