/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsStmt

abstract class RsStmtMixin(node: ASTNode) : RsElementImpl(node), RsStmt {
    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}
