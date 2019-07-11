/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsElementTypes.DEFAULT
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.psi.RsTraitAlias
import org.rust.lang.core.stubs.RsTraitAliasStub

val RsTraitAlias.default: PsiElement?
    get() = node.findChildByType(DEFAULT)?.psi

abstract class RsTraitAliasImplMixin : RsStubbedNamedElementImpl<RsTraitAliasStub>, RsTraitAlias {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsTraitAliasStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = iconWithVisibility(flags, RsIcons.TRAIT)

    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}
