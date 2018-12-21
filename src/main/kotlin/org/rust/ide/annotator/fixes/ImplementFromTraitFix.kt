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
import org.rust.ide.presentation.insertionSafeText
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTryExpr
import org.rust.lang.core.psi.ext.expandedMembers
import org.rust.lang.core.psi.ext.withSubst
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt

class ImplementFromTraitFix(
    rsTryExpr: RsTryExpr,
    private val fromTy: Ty,
    private val intoTy: Ty
) : LocalQuickFixAndIntentionActionOnPsiElement(rsTryExpr) {
    override fun getFamilyName(): String = text

    override fun getText(): String = "Create 'From<${fromTy.insertionSafeText}>' for type '${intoTy.insertionSafeText}'"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (intoTy is TyAdt) {
            val item = intoTy.item
            val traitItem = item.knownItems
                .From?.withSubst(fromTy) ?: return
            val members = traitItem.element.expandedMembers
            val templateItem = RsPsiFactory(project)
                .createMembers(members, traitItem.subst)

            val traitImplItem = RsPsiFactory(project)
                .createImpl(
                    "From<${fromTy.insertionSafeText}> for ${intoTy.insertionSafeText}",
                    templateItem.functionList
                )

            item.parent.addAfter(traitImplItem, item)
        }
    }
}
