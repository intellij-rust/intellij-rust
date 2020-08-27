/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.inline.InlineOptionsWithSearchSettingsDialog
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsInlineDialog(
    element: RsNameIdentifierOwner,
    private val refElement: RsReference?,
    project: Project,
    private val occurrencesNumber: Int
) : InlineOptionsWithSearchSettingsDialog(project, true, element) {
    private var searchInCommentsAndStrings = true
    private var searchInTextOccurrences = true

    abstract fun getLabelText(occurrences: String): String

    fun shouldBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

    override fun getNameLabelText(): String {
        val occurrencesString =
            if (occurrencesNumber < 0) {
                ""
            } else {
                buildString {
                    append("has $occurrencesNumber occurrence")
                    if (occurrencesNumber != 1) append("s")
                }
            }
        return getLabelText(occurrencesString)
    }

    override fun isInlineThis(): Boolean = false

    override fun isSearchInCommentsAndStrings() =
        searchInCommentsAndStrings

    override fun saveSearchInCommentsAndStrings(searchInComments: Boolean) {
        searchInCommentsAndStrings = searchInComments
    }

    override fun isSearchForTextOccurrences(): Boolean =
        searchInTextOccurrences

    override fun saveSearchInTextOccurrences(searchInTextOccurrences: Boolean) {
        this.searchInTextOccurrences = searchInTextOccurrences
    }

    final override fun init() {
        title = borderTitle
        myInvokedOnReference = refElement != null

        setPreviewResults(true)
        setDoNotAskOption(object : DoNotAskOption {
            override fun isToBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog
            override fun setToBeShown(value: Boolean, exitCode: Int) {
                EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog = value
            }

            override fun canBeHidden() = true
            override fun shouldSaveOptionsOnCancel() = false
            override fun getDoNotShowMessage() = "Do not show in future"
        })
        super.init()
    }
}

class RsInlineUsageViewDescriptor(val element: PsiElement, val header: String) : UsageViewDescriptor {
    override fun getCommentReferencesText(usagesCount: Int, filesCount: Int) =
        RefactoringBundle.message("comments.elements.header",
            UsageViewBundle.getOccurencesString(usagesCount, filesCount))

    override fun getCodeReferencesText(usagesCount: Int, filesCount: Int) =
        RefactoringBundle.message("invocations.to.be.inlined",
            UsageViewBundle.getReferencesString(usagesCount, filesCount))

    override fun getElements() = arrayOf(element)

    override fun getProcessedElementsHeader() = header
}
