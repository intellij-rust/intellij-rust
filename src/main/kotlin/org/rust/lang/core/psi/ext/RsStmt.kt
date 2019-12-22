/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.RsStmt
import javax.swing.Icon

abstract class RsStmtMixin(node: ASTNode) : RsElementImpl(node), RsStmt {
    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)

    override fun getIcon(flags: Int): Icon? {
        if (this is RsLetDecl) {
            val patBinding = (pat as? RsPatIdent)?.patBinding
            if (patBinding != null) {
                return patBinding.getIcon(flags)
            }
        }
        return super.getIcon(flags)
    }
}
