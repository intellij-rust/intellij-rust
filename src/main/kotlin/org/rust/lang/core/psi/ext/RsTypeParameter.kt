/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsPolybound
import org.rust.lang.core.psi.RsPsiImplUtil
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
            .filter { (it.typeReference?.skipParens() as? RsBaseType)?.path?.reference?.resolve() == this }
            .flatMap { it.typeParamBounds?.polyboundList.orEmpty() }

    return typeParamBounds?.polyboundList.orEmpty() + whereBounds
}

/**
 * Note that a type parameter can be sized or not sized in different contexts:
 *
 * ```
 *     impl<T: ?Sized> Box<T> {
 *         fn foo() -> Box<T> { unimplemented!() }
 *                       //^ `T` is NOT sized
 *         fn foo() -> Box<T> where T: Sized { unimplemented!() }
 *     }                 //^ `T` is sized
 * ```
 */
val RsTypeParameter.isSized: Boolean
    get() {
        // We just check `?` before trait name in bound because at this moment only `Sized` trait can have `?` modifier
        val owner = parent?.parent as? RsGenericDeclaration
        val whereBounds =
            owner?.whereClause?.wherePredList.orEmpty()
                .filter { (it.typeReference?.skipParens() as? RsBaseType)?.name == name }
                .flatMap { it.typeParamBounds?.polyboundList.orEmpty() }
        val bounds = typeParamBounds?.polyboundList.orEmpty() + whereBounds
        return bounds.none { it.hasQ }
    }

abstract class RsTypeParameterImplMixin : RsStubbedNamedElementImpl<RsTypeParameterStub>, RsTypeParameter {
    constructor(node: ASTNode) : super(node)
    constructor(stub: RsTypeParameterStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val declaredType: Ty get() = RsPsiTypeImplUtil.declaredType(this)

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getParameterUseScope(this) ?: super.getUseScope()
}
