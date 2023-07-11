/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import org.rust.RsBundle
import org.rust.openapiext.document

/**
 * Fix that removes the given range from the document and places a text onto its place.
 * @param fixName The name to use for the fix instead of the default one to better fit the inspection.
 * @param file
 * @param range The range *inside element* that will be removed from the document.
 * @param substitution The text that will be placed starting from `range.startOffset`. If `null`, no text will be inserted.
 */
class SubstituteTextFix private constructor(
    @IntentionName private val fixName: String = RsBundle.message("intention.name.substitute"),
    element: PsiElement,
    range: TextRange,
    private val substitution: String?
) : RsQuickFixBase<PsiElement>(element) {

    @SafeFieldForPreview
    private val fileWithRange = SmartPointerManager.getInstance(element.project)
        .createSmartPsiFileRangePointer(element.containingFile, range)

    override fun getText(): String = fixName
    override fun getFamilyName() = RsBundle.message("intention.family.name.substitute.one.text.to.another")

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val range = fileWithRange.range ?: return
        val document = element.containingFile.document ?: return
        if (substitution != null) {
            document.replaceString(range.startOffset, range.endOffset, substitution)
        } else {
            document.deleteString(range.startOffset, range.endOffset)
        }
    }

    companion object {
        fun delete(@IntentionName fixName: String, file: PsiFile, range: TextRange) =
            SubstituteTextFix(fixName, file.findElementAt(range.startOffset)!!, range, null)

        fun insert(@IntentionName fixName: String, file: PsiFile, offset: Int, text: String) =
            SubstituteTextFix(fixName, file.findElementAt(offset)!!, TextRange(offset, offset), text)

        fun replace(@IntentionName fixName: String, file: PsiFile, range: TextRange, text: String) =
            SubstituteTextFix(fixName, file.findElementAt(range.startOffset)!!, range, text)
    }
}
