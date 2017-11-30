/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiDirectory
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RsModItemStub
import javax.swing.Icon

abstract class RsModItemImplMixin : RsStubbedNamedElementImpl<RsModItemStub>,
                                    RsModItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsModItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.MODULE)

    override val isPublic: Boolean get() = RsPsiImplUtil.isPublic(this, stub)

    override val `super`: RsMod get() = containingMod

    override val modName: String? get() = name

    override val crateRelativePath: String? get() = RsPsiImplUtil.modCrateRelativePath(this)

    override val ownsDirectory: Boolean = true // Any inline nested mod owns a directory

    override val ownedDirectory: PsiDirectory? get() {
        val name = name ?: return null
        return `super`.ownedDirectory?.findSubdirectory(name)
    }

    override val isCrateRoot: Boolean = false

    override val innerAttrList: List<RsInnerAttr>
        get() = PsiTreeUtil.getStubChildrenOfTypeAsList(this, RsInnerAttr::class.java)

    override val outerAttrList: List<RsOuterAttr>
        get() = PsiTreeUtil.getStubChildrenOfTypeAsList(this, RsOuterAttr::class.java)

    override fun getContext() = ExpansionResult.getContextImpl(this)
}

val RsModItem.hasMacroUse: Boolean get() =
    queryAttributes.hasAttribute("macro_use")
