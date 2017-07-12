/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsMacroDefinition
import org.rust.lang.core.psi.RsMacroItem
import org.rust.lang.core.stubs.RsMacroItemStub

abstract class RsMacroItemImplMixin : RsStubbedNamedElementImpl<RsMacroItemStub>, RsMacroItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsMacroItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? {
        val macro = this.macro
        return when (macro) {
            is RsMacroDefinition -> macro.identifier
            else -> null
        }
    }
}

val RsMacroItem.hasMacroExport: Boolean get() =
    queryAttributes.hasAttribute("macro_export")
