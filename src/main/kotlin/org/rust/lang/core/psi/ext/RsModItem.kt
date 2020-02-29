/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsInnerAttr
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsModItemStub
import javax.swing.Icon

abstract class RsModItemImplMixin : RsStubbedNamedElementImpl<RsModItemStub>,
                                    RsModItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsModItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.MODULE)

    override val `super`: RsMod get() = containingMod

    override val modName: String? get() = name

    override val pathAttribute: String? get() = queryAttributes.lookupStringValueForKey("path")

    override val crateRelativePath: String? get() = RsPsiImplUtil.modCrateRelativePath(this)

    override val ownsDirectory: Boolean = true // Any inline nested mod owns a directory

    override val isCrateRoot: Boolean = false

    override val innerAttrList: List<RsInnerAttr>
        get() = stubChildrenOfType()

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getDeclarationUseScope(this) ?: super.getUseScope()
}

val RsModItem.hasMacroUse: Boolean get() =
    queryAttributes.hasAttribute("macro_use")
