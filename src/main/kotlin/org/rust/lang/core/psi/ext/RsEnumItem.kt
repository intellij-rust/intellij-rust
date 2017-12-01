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
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsEnumItemStub
import org.rust.lang.core.types.RsPsiTypeImplUtil
import org.rust.lang.core.types.ty.Ty
import javax.swing.Icon


abstract class RsEnumItemImplMixin : RsStubbedNamedElementImpl<RsEnumItemStub>, RsEnumItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsEnumItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? =
        iconWithVisibility(flags, RsIcons.ENUM)

    override val isPublic: Boolean get() = RsPsiImplUtil.isPublic(this, stub)

    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override val declaredType: Ty get() = RsPsiTypeImplUtil.declaredType(this)

    override fun getContext() = ExpansionResult.getContextImpl(this)
}
