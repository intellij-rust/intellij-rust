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
import org.rust.lang.core.psi.ext.elementType

@Suppress("UnnecessaryVariable")
class ReplaceBlockCommentWithLineCommentIntention : RsElementBaseIntentionAction<PsiComment>() {

    override fun getText(): String = familyName
    override fun getFamilyName(): String = "Replace with end of line comment"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): PsiComment? =
        element.ancestorOrSelf<PsiComment>()?.takeIf { it.elementType == BLOCK_COMMENT }

    override fun invoke(project: Project, editor: Editor, ctx: PsiComment) {
        val blockComment = ctx
        val factory = RsPsiFactory(project)

        val space = (blockComment.prevSibling as? PsiWhiteSpace)
            ?.text
            ?.substringAfterLast('\n')
            .orEmpty()

        val indent = factory.createWhitespace("\n$space")

        val lineCommentsTexts = blockComment.content
            .lines()
            .reversed()
            .map(String::trim)

        val lastIndex = lineCommentsTexts.size - 1
        val parent = blockComment.parent
        lineCommentsTexts.forEachIndexed { index, commentText ->
            val newLineComment = factory.createLineComment(" $commentText")
            parent.addAfter(newLineComment, blockComment)

            if (index != lastIndex) {
                parent.addAfter(indent, blockComment)
            }
        }

        blockComment.delete()
    }

    private val PsiComment.content: String
        get() = text
            .drop(BLOCK_COMMENT_DELIMITER_LEN)
            .dropLast(BLOCK_COMMENT_DELIMITER_LEN)
            .trim()

    companion object {
        const val BLOCK_COMMENT_DELIMITER_LEN: Int = 2 // the length of `/*` or `*/`
    }
}
