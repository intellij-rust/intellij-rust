/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsLifetimeParameterStub

val RsLifetimeParameter.bounds: List<RsLifetime>
    get() {
        val owner = parent?.parent as? RsGenericDeclaration
        val whereBounds = owner?.whereClause?.wherePredList.orEmpty()
            .filter { it.lifetime?.reference?.resolve() == this }
            .flatMap { it.lifetimeParamBounds?.lifetimeList.orEmpty() }
        return lifetimeParamBounds?.lifetimeList.orEmpty() + whereBounds
    }

abstract class RsLifetimeParameterImplMixin : RsStubbedNamedElementImpl<RsLifetimeParameterStub>, RsLifetimeParameter {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsLifetimeParameterStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement = quoteIdentifier

    override fun setName(name: String): PsiElement? {
        nameIdentifier.replace(RsPsiFactory(project).createQuoteIdentifier(name))
        return this
    }

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getParameterUseScope(this) ?: super.getUseScope()
}
