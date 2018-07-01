/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.psi.RsInnerAttr
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.RsOuterAttr
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsModItemStub
import org.rust.openapiext.findFileByMaybeRelativePath
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
        val directoryPath = (pathAttribute ?: name) ?: return null
        val superDir = `super`.ownedDirectory ?: return null
        val directory = superDir.virtualFile
            .findFileByMaybeRelativePath(FileUtil.toSystemIndependentName(directoryPath)) ?: return null
        return superDir.manager.findDirectory(directory)
    }

    override val isCrateRoot: Boolean = false

    override val innerAttrList: List<RsInnerAttr>
        get() = stubChildrenOfType()

    override val outerAttrList: List<RsOuterAttr>
        get() = stubChildrenOfType()

    override fun getContext(): PsiElement? = ExpansionResult.getContextImpl(this)
}

val RsModItem.hasMacroUse: Boolean get() =
    queryAttributes.hasAttribute("macro_use")

val RsModItem.pathAttribute: String? get() =
    queryAttributes.lookupStringValueForKey("path")
