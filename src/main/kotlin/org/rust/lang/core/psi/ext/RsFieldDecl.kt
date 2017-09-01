/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsFieldDecl
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsFieldDeclStub
import javax.swing.Icon

abstract class RsFieldDeclImplMixin : RsStubbedNamedElementImpl<RsFieldDeclStub>, RsFieldDecl {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RsFieldDeclStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.FIELD)

    override val isPublic: Boolean get() = RsPsiImplUtil.isPublic(this, stub)

}
