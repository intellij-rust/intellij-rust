/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.inspections.import.AutoImportFix
import org.rust.ide.refactoring.implementMembers.generateTraitMembers
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.endOffset

class ImplOperatorTraitFix(element: PsiElement,
                           private val traitName: String,
                           private val tyName: String
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    override fun getFamilyName(): String = "Impl `$traitName` operation"

    override fun getText(): String = familyName

    override fun startInWriteAction(): Boolean = false
    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        var traitImpl = RsPsiFactory(project).createImpl("$traitName for $tyName", emptyList())

        runWriteAction {
            traitImpl = file.add(traitImpl) as RsImplItem
        }

        if (editor == null) return

        val traitRef = traitImpl.traitRef ?: return
        PsiNavigationSupport.getInstance().createNavigatable(project, file.virtualFile, traitRef.endOffset).navigate(true)

        if (!isUnitTestMode) {
            // import trait
            AutoImportFix(traitRef.path, AutoImportFix.Type.GENERAL_PATH).invoke(project)

            // impl trait
            generateTraitMembers(traitImpl, editor)
        }
    }
}
