/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInsight.daemon.impl.actions.AbstractBatchSuppressByNoInspectionCommentFix
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.RsBundle
import org.rust.lang.core.psi.RS_COMMENTS
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.*

class RsInspectionSuppressor : InspectionSuppressor {
    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<out SuppressQuickFix> = arrayOf(
        SuppressInspectionFix(toolId),
        SuppressInspectionFix(SuppressionUtil.ALL)
    )

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean =
        if (element is RsFile) {
            element.childrenOfType<PsiComment>().asSequence().isSuppressedByComment(toolId)
        } else {
            element.ancestors.filterIsInstance<RsItemElement>()
                .any { it.leadingComments().isSuppressedByComment(toolId) }
        }

    private fun Sequence<PsiComment>.isSuppressedByComment(toolId: String): Boolean =
        this.any { comment ->
            val matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(comment.text)
            matcher.matches() && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId)
        }

    private class SuppressInspectionFix(id: String) : AbstractBatchSuppressByNoInspectionCommentFix(id, id == SuppressionUtil.ALL) {

        init {
            text = if (id == SuppressionUtil.ALL) RsBundle.message("intention.name.suppress.all.inspections.for.item") else RsBundle.message("intention.name.suppress.for.item.with.comment")
        }

        override fun getContainer(context: PsiElement?): PsiElement? {
            if (context == null) return null
            return context.ancestorOrSelf<RsItemElement>()
        }
    }
}

private fun RsItemElement.leadingComments(): Sequence<PsiComment> =
    generateSequence(firstChild) { psi -> psi.nextSibling.takeIf { it.elementType in RS_COMMENTS || it is PsiWhiteSpace } }
        .filterIsInstance<PsiComment>()
