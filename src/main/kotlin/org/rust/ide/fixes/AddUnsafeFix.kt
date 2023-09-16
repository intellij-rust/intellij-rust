/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.ide.injected.isDoctestInjectedMain
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.isActuallyUnsafe
import org.rust.lang.core.psi.ext.isMain
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.psi.ext.superItem

class AddUnsafeFix private constructor(element: PsiElement) : RsQuickFixBase<PsiElement>(element) {
    @Nls
    private val _text = run {
        @NlsSafe val item = when (element) {
            is RsBlockExpr -> "block"
            is RsImplItem -> "impl"
            else -> "function"
        }
        RsBundle.message("intention.name.add.unsafe.to", item)
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
                parent is RsFunction && !parent.isUnsafeApplicable -> null
                else -> AddUnsafeFix(parent)
            }
        }

        private val RsFunction.isUnsafeApplicable: Boolean
            get() {
                // Unsafe modifier cannot be added to main function or tests
                if (isActuallyUnsafe || isMain || isTest || isDoctestInjectedMain) return false

                val superFn = (superItem as? RsFunction) ?: return true
                // An implementing function cannot be unsafe unless the trait function is unsafe as well
                return superFn.isUnsafe
            }
    }
}
