/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsSelfParameter
import org.rust.lang.core.stubs.RsSelfParameterStub
import org.rust.lang.core.types.ty.Mutability


val RsSelfParameter.mutability: Mutability get() = Mutability.valueOf(greenStub?.isMut ?: (mut != null))
val RsSelfParameter.isRef: Boolean get() = greenStub?.isRef ?: (and != null)
val RsSelfParameter.isExplicitType get() = greenStub?.isExplicitType ?: (colon != null)
val RsSelfParameter.parentFunction: RsFunction get() = ancestorStrict()!!

abstract class RsSelfParameterImplMixin : RsStubbedElementImpl<RsSelfParameterStub>, RsSelfParameter {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsSelfParameterStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement = self
    override fun getName(): String = "self"

    override fun setName(name: String): PsiElement? {
        // can't rename self
        throw UnsupportedOperationException()
    }

    override fun getTextOffset(): Int = nameIdentifier.textOffset

    override fun getIcon(flags: Int) = RsIcons.ARGUMENT
}
