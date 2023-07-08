/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.block

class AddMainFnFix(file: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(file) {

    override fun getFamilyName(): String = RsBundle.message("intention.family.name.add.fn.main")

    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val function = file.add(RsPsiFactory(project).createFunction("fn main() { }")) as RsFunction
        val offset = function.block?.lbrace?.textOffset ?: return
        editor?.caretModel?.moveToOffset(offset + 1)
    }
}
