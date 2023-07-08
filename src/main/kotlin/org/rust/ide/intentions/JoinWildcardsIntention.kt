/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset

class JoinWildcardsIntention : RsElementBaseIntentionAction<List<RsPatWild>>() {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.replace.successive.with")

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): List<RsPatWild>? {
        var patUnderCaret = element.ancestorStrict<RsPat>() ?: return null
        if (patUnderCaret is RsPatWild) {
            patUnderCaret = patUnderCaret.ancestorStrict() ?: return null
        }
        val patList = when (patUnderCaret) {
            is RsPatTup -> patUnderCaret.patList
            is RsPatTupleStruct -> patUnderCaret.patList
            is RsPatSlice -> patUnderCaret.patList
            // note that RsOrPat also has (and allows) multiple _ wildcards.
            // it does, however, not support `..`, so we leave it out for this inspection.
            else -> return null
        }

        // Unavailable if `..` is already there
        if (patList.any { it is RsPatRest }) return null

        val patWildSeq = mutableListOf<RsPatWild>()
        for (pat in patList.asSequence() + sequenceOf(null)) {
            if (pat is RsPatWild) {
                patWildSeq += pat
            } else {
                if (patWildSeq.size > 0) {
                    val patWildSeqRange = TextRange(patWildSeq.first().startOffset, patWildSeq.last().endOffset)
                    if (element.startOffset in patWildSeqRange) {
                        text = if (patWildSeq.size == 1) RsBundle.message("intention.name.replace.with") else familyName
                        if (!PsiModificationUtil.canReplaceAll(patWildSeq)) return null
                        return patWildSeq
                    }
                }
                patWildSeq.clear()
            }
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: List<RsPatWild>) {
        val startOffset = ctx.first().startOffset
        val endOffset = ctx.last().endOffset
        editor.document.replaceString(startOffset, endOffset, "..")
        editor.caretModel.moveToOffset(startOffset + 2)
    }
}
