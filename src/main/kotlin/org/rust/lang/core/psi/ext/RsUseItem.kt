/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.stubs.RsUseItemStub

abstract class RsUseItemImplMixin : RsStubbedElementImpl<RsUseItemStub>, RsUseItem {

    constructor (node: ASTNode) : super(node)
    constructor (stub: RsUseItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}

val HAS_PRELUDE_IMPORT_PROP: StubbedAttributeProperty<RsUseItem, RsUseItemStub> =
    StubbedAttributeProperty({ it.hasAttribute("prelude_import") }, RsUseItemStub::mayHavePreludeImport)

val RsUseItem.isReexport: Boolean
    get() = visibility != RsVisibility.Private
