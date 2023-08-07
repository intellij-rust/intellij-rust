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

class EscapeKeywordFix(
    element: PsiElement,
    private val isKeyword: Boolean
) : RsQuickFixBase<PsiElement>(element) {

    override fun getFamilyName(): String = RsBundle.message("intention.name.escape.keyword")
    override fun getText(): String = if (isKeyword) RsBundle.message("intention.name.escape.keyword") else RsBundle.message("intention.name.escape.reserved.keyword")

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val name = element.text
        element.replace(RsPsiFactory(project).createIdentifier("r#${name}"))
    }
}
