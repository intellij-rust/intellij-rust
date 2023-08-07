/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsElement

class EncloseExprInBracesFix(element: RsElement) : RsQuickFixBase<PsiElement>(element) {
    override fun getFamilyName(): String = text
    override fun getText(): String = RsBundle.message("intention.name.enclose.expression.in.braces")

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val enclosed = RsPsiFactory(project).createExpression("{ ${element.text} }")
        element.replace(enclosed)
    }
}
