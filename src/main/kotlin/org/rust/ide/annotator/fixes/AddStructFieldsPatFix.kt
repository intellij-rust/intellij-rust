/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.utils.expandStructFields
import org.rust.ide.utils.expandTupleStructFields
import org.rust.lang.core.psi.RsPatStruct
import org.rust.lang.core.psi.RsPatTupleStruct
import org.rust.lang.core.psi.RsPsiFactory

class AddStructFieldsPatFix(
    element: PsiElement
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getText() = "Add missing fields"

    override fun getFamilyName() = text

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, pat: PsiElement, endElement: PsiElement) {
        val factory = RsPsiFactory(project)
        if (pat is RsPatStruct) {
            expandStructFields(factory, pat)
        } else if (pat is RsPatTupleStruct) {
            expandTupleStructFields(factory, editor, pat)
        }
    }
}
