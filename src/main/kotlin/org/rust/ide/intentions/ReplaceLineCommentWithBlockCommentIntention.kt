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
import org.rust.lang.core.parser.RustParserDefinition.Companion.EOL_COMMENT
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.getNextNonWhitespaceSibling
import org.rust.lang.core.psi.ext.getPrevNonWhitespaceSibling
import java.lang.StringBuilder

class ReplaceLineCommentWithBlockCommentIntention : RsElementBaseIntentionAction<ReplaceLineCommentWithBlockCommentIntention.Context>() {

    override fun getText(): String = familyName
    override fun getFamilyName(): String = "Replace with block comment"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val comment = element.ancestorOrSelf<PsiComment>() ?: return null
        if (!comment.isEndOfLineComment()) {
            return null
        }

        var firstComment = comment
        while (true) {
            val previous = firstComment.getPrevNonWhitespaceSibling() ?: break
            if (!previous.isEndOfLineComment()) {
                break
            }
            firstComment = previous as PsiComment
        }

        return Context(firstComment)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val firstComment = ctx.firstComment

        val indent = (firstComment.prevSibling as? PsiWhiteSpace)
            ?.text
            ?.reversed()
            ?.takeWhile { it == ' ' || it == '\t' } ?: ""

        val comments = mutableListOf(firstComment)
        var nextComment = firstComment
        while (true) {
            nextComment = nextComment.nextComment() ?: break
            comments.add(nextComment)
        }

        val blockComment = if (comments.size == 1)
            " ${comments.first().commentText()} "
        else
            comments.joinToString(separator = "\n", prefix = "\n", postfix = "\n$indent") {
                "$indent${it.commentText()}"
            }

        comments.drop(1).forEach {
            (it.prevSibling as? PsiWhiteSpace)?.delete()
            it.delete()
        }
        firstComment.replace(RsPsiFactory(project).createBlockComment(blockComment))
    }

    private fun PsiElement.isEndOfLineComment() = node.elementType == EOL_COMMENT

    private fun PsiComment.commentText() = text.substring(2)
        .replace("/*", "/ *")
        .replace("*/", "* /")
        .trim()

    private fun PsiComment.nextComment(): PsiComment? {
        return (getNextNonWhitespaceSibling() as? PsiComment)?.takeIf { it.isEndOfLineComment() }
    }

    data class Context(
        val firstComment: PsiComment
    )
}
