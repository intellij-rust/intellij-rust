/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.intentions.RemoveCurlyBracesIntention
import org.rust.lang.core.psi.RsUseGroup
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.deleteWithSurroundingCommaAndWhitespace
import org.rust.lang.core.psi.ext.parentUseSpeck


/**
 * Fix that removes a use speck or a whole use item.
 */
class RemoveImportFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {
    override fun getText() = "Remove unused import"
    override fun getFamilyName() = text

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val element = startElement as? RsElement ?: return
        deleteUseSpeckOrUseItem(element)
    }
}

private fun deleteUseSpeckOrUseItem(element: RsElement) {
    val parent = element.parent
    element.deleteWithSurroundingCommaAndWhitespace()

    if (parent is RsUseGroup) {
        val parentSpeck = parent.parentUseSpeck
        if (parent.useSpeckList.isEmpty()) {
            deleteUseSpeck(parentSpeck)
        } else {
            val ctx = RemoveCurlyBracesIntention.createContextIfCompatible(parentSpeck) ?: return
            RemoveCurlyBracesIntention.removeCurlyBracesFromUseSpeck(ctx)
        }
    }
}

fun deleteUseSpeck(useSpeck: RsUseSpeck) {
    val element = (useSpeck.parent as? RsUseItem) ?: useSpeck
    deleteUseSpeckOrUseItem(element)
}
