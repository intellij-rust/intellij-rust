/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPsiFactory

class EscapeKeywordFix(
    element: PsiElement,
    private val isKeyword: Boolean
) : RsQuickFixBase<PsiElement>(element) {

    override fun getFamilyName(): String = "Escape keyword"
    override fun getText(): String = if (isKeyword) "Escape keyword" else "Escape reserved keyword"

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val name = element.text
        element.replace(RsPsiFactory(project).createIdentifier("r#${name}"))
    }
}
