/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsReadWriteAccessDetector : ReadWriteAccessDetector() {
    override fun isReadWriteAccessible(element: PsiElement): Boolean =
        element is RsPatBinding || element is RsFieldDecl

    override fun getReferenceAccess(referencedElement: PsiElement, reference: PsiReference): Access =
        getExpressionAccess(reference.element)

    override fun getExpressionAccess(element: PsiElement): Access {
        val expr = when (element) {
            is RsExpr -> element
            is RsPath -> element.parent as? RsPathExpr ?: return Access.Read
            is RsFieldLookup -> element.parentDotExpr
            is RsStructLiteralField -> return Access.Write // Struct literal `S { f: 0 }`
            else -> return Access.Read
        }

        val context = expr.context
        return if (context is RsBinaryExpr) {
            // `a = 1;` or `a += 1;`
            if (context.left == expr) {
                when (context.operatorType) {
                    AssignmentOp.EQ -> Access.Write
                    is ArithmeticAssignmentOp -> Access.ReadWrite
                    else -> Access.Read
                }
            } else {
                Access.Read
            }
        } else if (context is RsUnaryExpr && context.isDereference) {
            getExpressionAccess(context)
        } else {
            Access.Read
        }
    }

    override fun isDeclarationWriteAccess(element: PsiElement): Boolean =
        false
}
