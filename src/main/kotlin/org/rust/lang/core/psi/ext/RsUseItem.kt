/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsUseItemStub

abstract class RsUseItemImplMixin : RsStubbedElementImpl<RsUseItemStub>, RsUseItem {

    constructor (node: ASTNode) : super(node)
    constructor (stub: RsUseItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RsPsiImplUtil.isPublic(this, stub)

    override fun getContext() = ExpansionResult.getContextImpl(this)
}
