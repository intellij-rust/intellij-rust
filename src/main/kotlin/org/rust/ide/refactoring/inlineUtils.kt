/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ListItem
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.inline.InlineOptionsDialog
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsInlineDialog(
    element: RsNameIdentifierOwner,
    private val refElement: RsReference?,
    project: Project,
) : InlineOptionsDialog(project, true, element) {

    protected fun getOccurrencesText(occurrences: Int): String =
        when {
            occurrences < 0 -> ""
            occurrences == 1 -> "has 1 occurrence"
            else -> "has $occurrences occurrences"
        }

    override fun isInlineThis(): Boolean = false

    final override fun init() {
        title = borderTitle
        myInvokedOnReference = refElement != null

        setPreviewResults(true)
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
