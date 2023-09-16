/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsLabelDecl
import org.rust.lang.core.psi.RsPsiFactory

// Guaranteed by the grammar
val RsLabelDecl.owner: RsLabeledExpression
    get() = parent as RsLabeledExpression

abstract class RsLabelDeclImplMixin(type: IElementType) : RsNamedElementImpl(type), RsLabelDecl {
    override fun getNameIdentifier(): PsiElement? = quoteIdentifier

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RsPsiFactory(project).createQuoteIdentifier(name))
        return this
    }
}
