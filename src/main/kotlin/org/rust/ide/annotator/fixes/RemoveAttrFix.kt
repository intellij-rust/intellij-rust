/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.ext.RsAttr
import org.rust.lang.core.psi.ext.name

class RemoveAttrFix(attr: RsAttr) : LocalQuickFixOnPsiElement(attr) {
    private val _text = run {
        val suffix = attr.metaItem.name?.let { " `$it`" } ?: ""

        "Remove attribute$suffix"
    }

    override fun getText() = _text
    override fun getFamilyName() = "Remove attribute"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val attr = startElement as? RsAttr ?: return
        attr.delete()
    }
}
