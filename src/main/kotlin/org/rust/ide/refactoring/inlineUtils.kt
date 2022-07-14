/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ListItem
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
) : InlineOptionsWithSearchSettingsDialog(project, true, element) {
    private var searchInCommentsAndStrings = true
    private var searchInTextOccurrences = true

    fun shouldBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

    protected fun getOccurrencesText(occurrences: Int): String =
        when {
            occurrences < 0 -> ""
            occurrences == 1 -> "has 1 occurrence"
            else -> "has $occurrences occurrences"
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
        setDoNotAskOption(object : com.intellij.openapi.ui.DoNotAskOption {
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

class RsInlineUsageViewDescriptor(
    val element: PsiElement,
    @Suppress("UnstableApiUsage") @ListItem val header: String
) : UsageViewDescriptor {
    override fun getCommentReferencesText(usagesCount: Int, filesCount: Int) =
        RefactoringBundle.message("comments.elements.header",
            UsageViewBundle.getOccurencesString(usagesCount, filesCount))

    @Suppress("InvalidBundleOrProperty")
    override fun getCodeReferencesText(usagesCount: Int, filesCount: Int) =
        RefactoringBundle.message("invocations.to.be.inlined",
            UsageViewBundle.getReferencesString(usagesCount, filesCount))

    override fun getElements() = arrayOf(element)

    override fun getProcessedElementsHeader() = header
}
