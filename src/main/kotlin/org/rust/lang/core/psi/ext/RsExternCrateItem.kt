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
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.resolve.ref.RsExternCrateReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsExternCrateItemStub

abstract class RsExternCrateItemImplMixin : RsStubbedNamedElementImpl<RsExternCrateItemStub>,
                                            RsExternCrateItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsExternCrateItemStub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override fun getReference(): RsReference = RsExternCrateReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = name!!

    override val isPublic: Boolean get() = RsPsiImplUtil.isPublic(this, stub)

    override fun getIcon(flags: Int) = RsIcons.CRATE

    override fun getContext() = ExpansionResult.getContextImpl(this)
}

val RsExternCrateItem.hasMacroUse: Boolean get() =
    queryAttributes.hasAttribute("macro_use")
