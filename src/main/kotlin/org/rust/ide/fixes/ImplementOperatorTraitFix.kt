/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.refactoring.implementMembers.generateMissingTraitMembers
import org.rust.ide.utils.getTopmostParentInside
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.types.ty.Ty
import org.rust.openapiext.createSmartPointer

class ImplementOperatorTraitFix(
    element: PsiElement,
    trait: RsTraitItem,
    private val lhsType: Ty,
    private val rhsType: Ty,
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    private val traitPointer: SmartPsiElementPointer<RsTraitItem> = trait.createSmartPointer()
    private val traitName: String? = trait.name

    override fun getText(): String = "Implement `$traitName` trait"
    override fun getFamilyName(): String = "Implement operator trait"

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val context = startElement as? RsBinaryExpr ?: return
        val containingMod = context.ancestorStrict<RsMod>() ?: return
        val containingItem = context.getTopmostParentInside(containingMod)

        RsImportHelper.importElement(context, traitPointer.element ?: return)
        val lhsTypeText = lhsType.renderInsertionSafe(context)
        val typeArguments = if (lhsType != rhsType) {
            val rhsTypeText = rhsType.renderInsertionSafe(context)
            "<$rhsTypeText>"
        } else {
            ""
        }

        var traitImpl = RsPsiFactory(project).createImpl("$traitName$typeArguments for $lhsTypeText", emptyList())
        traitImpl = containingMod.addBefore(traitImpl, containingItem) as RsImplItem

        generateMissingTraitMembers(traitImpl, editor)
    }

    // No intention preview because adding imports is not yet supported in preview
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY
}
