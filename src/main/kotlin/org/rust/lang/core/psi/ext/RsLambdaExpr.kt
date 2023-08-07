/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsLambdaExpr
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyFunctionBase
import org.rust.lang.core.types.type

val RsLambdaExpr.async: PsiElement?
    get() = node.findChildByType(RsElementTypes.ASYNC)?.psi

val RsLambdaExpr.isAsync: Boolean
    get() = async != null

val RsLambdaExpr.isConst: Boolean
    get() = node.findChildByType(RsElementTypes.CONST) != null

val RsLambdaExpr.valueParameters: List<RsValueParameter>
    get() = valueParameterList.valueParameterList

val RsLambdaExpr.returnType: Ty?
    get() = (type as? TyFunctionBase)?.retType
