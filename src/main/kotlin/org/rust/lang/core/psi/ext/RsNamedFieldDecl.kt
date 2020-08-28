/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsNamedFieldDecl
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsNamedFieldDeclStub
import javax.swing.Icon

abstract class RsNamedFieldDeclImplMixin : RsStubbedNamedElementImpl<RsNamedFieldDeclStub>, RsNamedFieldDecl {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RsNamedFieldDeclStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        if (owner is RsEnumVariant) RsIcons.FIELD else iconWithVisibility(flags, RsIcons.FIELD)

    // temporary solution.
    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getDeclarationUseScope(this) ?: super.getUseScope()
}
