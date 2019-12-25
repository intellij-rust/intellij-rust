/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.psi.RsTupleFieldDecl
import org.rust.lang.core.stubs.RsPlaceholderStub
import javax.swing.Icon

val RsTupleFieldDecl.position: Int?
    get() = parentStruct?.positionalFields?.withIndex()?.firstOrNull { it.value === this }?.index


abstract class RsTupleFieldDeclImplMixin : RsStubbedElementImpl<RsPlaceholderStub>, RsTupleFieldDecl {
    constructor(node: ASTNode) : super(node)
    constructor(stub: RsPlaceholderStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.FIELD)

    override fun getName(): String? = position?.toString()

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getDeclarationUseScope(this) ?: super.getUseScope()
}
