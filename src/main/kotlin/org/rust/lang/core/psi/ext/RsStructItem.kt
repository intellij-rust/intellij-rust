/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.psi.RsElementTypes.UNION
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsStructItemStub
import org.rust.lang.core.types.RsPsiTypeImplUtil
import org.rust.lang.core.types.ty.Ty
import javax.swing.Icon

val RsStructItem.union: PsiElement?
    get() = node.findChildByType(UNION)?.psi


enum class RsStructKind {
    STRUCT,
    UNION
}

val RsStructItem.kind: RsStructKind get() {
    val hasUnion = stub?.isUnion ?: (union != null)
    return if (hasUnion) RsStructKind.UNION else RsStructKind.STRUCT
}

abstract class RsStructItemImplMixin : RsStubbedNamedElementImpl<RsStructItemStub>, RsStructItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsStructItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.STRUCT)

    override val isPublic: Boolean get() = RsPsiImplUtil.isPublic(this, stub)

    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override val declaredType: Ty get() = RsPsiTypeImplUtil.declaredType(this)

    override fun getContext(): RsElement = ExpansionResult.getContextImpl(this)
}
