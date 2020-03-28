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
import org.rust.lang.core.psi.ext.ancestorOrSelf

class ReplaceBlockCommentWithLineCommentIntention : RsElementBaseIntentionAction<PsiComment>() {

    override fun getText(): String = familyName
    override fun getFamilyName(): String = "Replace with end of line comment"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): PsiComment? {
        val comment = element.ancestorOrSelf<PsiComment>() ?: return null
        if (BLOCK_COMMENT != comment.tokenType) {
            return null
        }
        return comment
    }

    override fun invoke(project: Project, editor: Editor, ctx: PsiComment) {
        val factory = RsPsiFactory(project)

        val indent = if (ctx.prevSibling is PsiWhiteSpace) {
            val space = ctx.prevSibling
                .text
                .reversed()
                .takeWhile { it == ' ' || it == '\t' }

            factory.createWhitespace("\n$space")
        } else {
            factory.createNewline()
        }

        val comments = ctx.text
            .substring(2, ctx.text.length - 2)
            .trim()
            .split("\n")
            .reversed()

        val lastIndex = comments.size - 1
        val parent = ctx.parent
        comments.forEachIndexed { index, comment ->
            val commentText = comment.trim()
            parent.addAfter(factory.createLineComment(commentText), ctx)

            if (index != lastIndex) {
                parent.addAfter(indent, ctx)
            }
        }

        ctx.delete()
    }
}
