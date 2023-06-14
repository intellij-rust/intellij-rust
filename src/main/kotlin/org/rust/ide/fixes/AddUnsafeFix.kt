/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.isMain

class AddUnsafeFix private constructor(element: PsiElement) : RsQuickFixBase<PsiElement>(element) {
    private val _text = run {
        val item = when (element) {
            is RsBlockExpr -> "block"
            is RsImplItem -> "impl"
            else -> "function"
        }
        "Add unsafe to $item"
    }

    override fun getFamilyName() = text
    override fun getText() = _text

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val unsafe = RsPsiFactory(project).createUnsafeKeyword()

        when (element) {
            is RsBlockExpr -> element.addBefore(unsafe, element.block)
            is RsFunction -> element.addBefore(unsafe, element.fn)
            is RsImplItem -> element.addBefore(unsafe, element.impl)
            else -> error("unreachable")
        }
    }

    companion object {
        fun create(element: PsiElement): AddUnsafeFix? {
            val parent = PsiTreeUtil.getParentOfType(
                element,
                RsBlockExpr::class.java,
                RsFunction::class.java,
                RsImplItem::class.java
            ) ?: return null

            return when {
                parent is RsFunction && parent.isMain -> null
                else -> AddUnsafeFix(parent)
            }
        }
    }
}
