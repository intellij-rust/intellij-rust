/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringFactory
import org.rust.RsBundle
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.openapiext.nonBlocking

/**
 * Fix that renames the given element.
 * @param element The element to be renamed.
 * @param newName The new name for the element.
 * @param fixName The name to use for the fix instead of the default one to better fit the inspection.
 */
class RenameFix(
    element: PsiNamedElement,
    val newName: String,
    @IntentionName private val fixName: String = RsBundle.message("intention.name.rename.to", newName)
) : RsQuickFixBase<PsiNamedElement>(element) {
    override fun getText() = fixName
    override fun getFamilyName() = RsBundle.message("intention.family.name.rename.element")

    override fun invoke(project: Project, editor: Editor?, element: PsiNamedElement) =
        project.nonBlocking({
            (element as? RsModDeclItem)?.reference?.resolve() ?: element
        }, {
            RefactoringFactory.getInstance(project).createRename(it, newName).run()
        })

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY
}
