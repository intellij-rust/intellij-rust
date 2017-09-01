/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsTupleFieldDecl
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsPlaceholderStub

abstract class RsTupleFieldDeclImplMixin : RsStubbedElementImpl<RsPlaceholderStub>, RsTupleFieldDecl {
    constructor(node: ASTNode) : super(node)
    constructor(stub: RsPlaceholderStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RsPsiImplUtil.isPublicNonStubbed(this)
}
