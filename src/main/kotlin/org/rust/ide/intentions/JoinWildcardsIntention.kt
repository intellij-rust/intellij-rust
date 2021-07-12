/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset

class JoinWildcardsIntention : RsElementBaseIntentionAction<List<RsPatWild>>() {
    override fun getFamilyName(): String = "Join wildcards into `..`"
    override fun getText(): String = familyName

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): List<RsPatWild>? {
        val patList = when (val pat = element.ancestorStrict<RsPat>() ?: return null) {
            is RsPatTup -> pat.patList
            is RsPatTupleStruct -> pat.patList
            is RsPatSlice -> pat.patList
            // note that RsOrPat also has (and allows) multiple _ wildcards.
            // it does, however, not support `..`, so we leave it out for this inspection.
            else -> return null
        }
        val wildcards = patList.takeLastWhile { it is RsPatWild }.map { it as RsPatWild }

        // There is more than one wildcard, thus it is useful to join to `..`
        // Replacing one (or zero) wildcards with one `..` is not useful.
        return if (wildcards.size > 1) {
            wildcards
        } else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: List<RsPatWild>) {
        val startOffset = ctx.first().startOffset
        val endOffset = ctx.last().endOffset
        editor.document.replaceString(startOffset, endOffset, "..")
    }
}
