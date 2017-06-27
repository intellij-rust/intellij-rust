/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RustPsiImplUtil
import org.rust.lang.core.stubs.RsUseItemStub

val RsUseItem.isStarImport: Boolean get() = stub?.isStarImport ?: (mul != null) // I hate operator precedence

abstract class RsUseItemImplMixin : RsStubbedElementImpl<RsUseItemStub>, RsUseItem {

    constructor (node: ASTNode) : super(node)
    constructor (stub: RsUseItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)
}
