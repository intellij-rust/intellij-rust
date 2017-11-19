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

/**
 * Returns all bounds for type parameter.
 *
 * Don't use it for stub creation because it will cause [com.intellij.openapi.project.IndexNotReadyException]!
 */
val RsTypeParameter.bounds: List<RsPolybound> get() {
    val owner = parent?.parent as? RsGenericDeclaration
    val whereBounds =
        owner?.whereClause?.wherePredList.orEmpty()
            .filter { (it.typeReference?.typeElement as? RsBaseType)?.path?.reference?.resolve() == this }
            .flatMap { it.typeParamBounds?.polyboundList.orEmpty() }

    return typeParamBounds?.polyboundList.orEmpty() + whereBounds
}

val RsTypeParameter.isSized: Boolean get() {
    val stub = stub
    if (stub != null) return stub.isSized

    // We can't use `resolve` here to find `?Sized` bound because it causes `IndexNotReadyException` while indexing.
    // Instead of it we just check `?` before trait name in bound
    // because at this moment only `Sized` trait can have `?` modifier
    val owner = parent?.parent as? RsGenericDeclaration
    val whereBounds =
        owner?.whereClause?.wherePredList.orEmpty()
            .filter { (it.typeReference?.typeElement as? RsBaseType)?.name == name }
            .flatMap { it.typeParamBounds?.polyboundList.orEmpty() }
    val bounds = typeParamBounds?.polyboundList.orEmpty() + whereBounds
    return bounds.none { it.q != null }
}

abstract class RsTypeParameterImplMixin : RsStubbedNamedElementImpl<RsTypeParameterStub>, RsTypeParameter {
    constructor(node: ASTNode) : super(node)
    constructor(stub: RsTypeParameterStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val declaredType: Ty get() = RsPsiTypeImplUtil.declaredType(this)
}
