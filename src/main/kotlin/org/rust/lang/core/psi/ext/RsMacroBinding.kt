/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsMacroBinding
import org.rust.lang.core.psi.tokenSetOf

abstract class RsMacroBindingImplMixin(node: ASTNode) : RsNamedElementImpl(node), RsMacroBinding {

    override fun getNameIdentifier(): PsiElement? = nameElement
}

val RsMacroBinding.nameElement: PsiElement? get() = macroNameElement(node)

val RsMacroBinding.fragmentSpecifier: String?
    get() = colon.getNextNonCommentSibling()?.text


fun macroNameElement(node: ASTNode): PsiElement? {
    return node.findChildByType(MACRO_IDENTS)?.psi
}

private val MACRO_IDENTS = tokenSetOf(RsElementTypes.TYPE_KW, RsElementTypes.CRATE, RsElementTypes.IDENTIFIER)
