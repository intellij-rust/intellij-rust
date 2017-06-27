/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLabelDecl
import org.rust.lang.core.psi.RsPsiFactory

abstract class RsLabelDeclImplMixin(node: ASTNode) : RsNamedElementImpl(node), RsLabelDecl {
    override fun getNameIdentifier(): PsiElement? = quoteIdentifier

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RsPsiFactory(project).createQuoteIdentifier(name))
        return this
    }
}
