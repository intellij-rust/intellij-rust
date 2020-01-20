/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.parser.RustParserDefinition.Companion.BLOCK_COMMENT
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.*

class ReplaceBlockCommentWithLineCommentIntention : RsElementBaseIntentionAction<ReplaceBlockCommentWithLineCommentIntention.Context>() {

    override fun getText(): String = familyName
    override fun getFamilyName(): String = "Replace with end of line comment"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val comment = element.ancestorOrSelf<PsiComment>() ?: return null
        if (BLOCK_COMMENT != comment.tokenType) {
            return null
        }
        return Context(comment)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val selected = ctx.comment
        val factory = RsPsiFactory(project)

        val indent = if (selected.prevSibling is PsiWhiteSpace) {
            val space = selected.prevSibling.text.reversed().takeWhile {  it == ' ' || it == '\t' }
            factory.createWhitespace("\n$space")
        } else {
            factory.createNewline()
        }

        val comments = selected.text
            .substring(2, selected.text.length - 2)
            .trim()
            .split("\n")
            .reversed()

        val lastIndex = comments.size - 1
        val parent = selected.parent
        comments.forEachIndexed { index, comment ->
            val commentText = comment.trim()
            parent.addAfter(factory.createLineComment(commentText), selected)
            if (index != lastIndex) parent.addAfter(indent, selected)
        }

        selected.delete()
    }

    data class Context(
        val comment: PsiComment
    )
}
