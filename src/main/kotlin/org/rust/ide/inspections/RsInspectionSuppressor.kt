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
import org.rust.lang.core.psi.RS_COMMENTS
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.elementType

class RsInspectionSuppressor : InspectionSuppressor {
    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<out SuppressQuickFix> = arrayOf(
        SuppressInspectionFix(toolId),
        SuppressInspectionFix(SuppressionUtil.ALL)
    )

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean =
        element.ancestors.filterIsInstance<RsItemElement>()
            .any { isSuppressedByComment(it, toolId) }

    private fun isSuppressedByComment(element: RsItemElement, toolId: String): Boolean {
        return element.leadingComments().any { comment ->
            val matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(comment.text)
            matcher.matches() && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId)
        }
    }

    private class SuppressInspectionFix(
        ID: String
    ) : AbstractBatchSuppressByNoInspectionCommentFix(ID, /* replaceOthers = */ ID == SuppressionUtil.ALL) {

        init {
            text = if (ID == SuppressionUtil.ALL) "Suppress all inspections for item" else "Suppress for item"
        }

        override fun getContainer(context: PsiElement?): PsiElement? {
            if (context == null) return null
            return context.ancestorOrSelf<RsItemElement>()
        }
    }
}

private fun RsItemElement.leadingComments(): Sequence<PsiComment>
    = generateSequence(firstChild) { psi -> psi.nextSibling.takeIf { it.elementType in RS_COMMENTS || it is PsiWhiteSpace } }
    .filterIsInstance<PsiComment>()
