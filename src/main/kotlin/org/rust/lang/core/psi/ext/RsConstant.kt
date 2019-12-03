/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsElementTypes.DEFAULT
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsConstantStub
import org.rust.lang.core.types.ty.Mutability

enum class RsConstantKind {
    STATIC,
    MUT_STATIC,
    CONST
}

val RsConstant.isMut: Boolean get() = greenStub?.isMut ?: (mut != null)

val RsConstant.isConst: Boolean get() = greenStub?.isConst ?: (const != null)

val RsConstant.kind: RsConstantKind get() = when {
    isMut -> RsConstantKind.MUT_STATIC
    isConst -> RsConstantKind.CONST
    else -> RsConstantKind.STATIC
}

val RsConstant.default: PsiElement?
    get() = node.findChildByType(DEFAULT)?.psi

val RsConstant.mutability: Mutability get() = Mutability.valueOf(isMut)

val RsConstant.nameLikeElement: PsiElement
    get() = nameIdentifier ?: underscore ?: error("Constant without name: `$text`")

abstract class RsConstantImplMixin : RsStubbedNamedElementImpl<RsConstantStub>, RsConstant {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RsConstantStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = iconWithVisibility(flags, when (kind) {
        RsConstantKind.CONST -> RsIcons.CONSTANT
        RsConstantKind.MUT_STATIC -> RsIcons.MUT_STATIC
        RsConstantKind.STATIC -> RsIcons.STATIC
    })

    override val isAbstract: Boolean get() = expr == null

    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getDeclarationUseScope(this) ?: super.getUseScope()
}
