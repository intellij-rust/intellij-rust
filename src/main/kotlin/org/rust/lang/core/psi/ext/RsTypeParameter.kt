/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsPolybound
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.stubs.RsTypeParameterStub
import org.rust.lang.core.types.RsPsiTypeImplUtil
import org.rust.lang.core.types.ty.Ty

val RsTypeParameter.bounds: List<RsPolybound> get() {
    val owner = parent?.parent as? RsGenericDeclaration
    val whereBounds =
        owner?.whereClause?.wherePredList.orEmpty()
            .filter { (it.typeReference?.typeElement as? RsBaseType)?.path?.reference?.resolve() == this }
            .flatMap { it.typeParamBounds?.polyboundList.orEmpty() }

    return typeParamBounds?.polyboundList.orEmpty() + whereBounds
}

abstract class RsTypeParameterImplMixin : RsStubbedNamedElementImpl<RsTypeParameterStub>, RsTypeParameter {
    constructor(node: ASTNode) : super(node)
    constructor(stub: RsTypeParameterStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val declaredType: Ty get() = RsPsiTypeImplUtil.declaredType(this)
}
