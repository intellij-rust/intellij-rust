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
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonWhitespaceSibling
import org.rust.lang.core.psi.ext.getPrevNonWhitespaceSibling

@Suppress("UnnecessaryVariable")
class ReplaceLineCommentWithBlockCommentIntention : RsElementBaseIntentionAction<PsiComment>() {

    override fun getText(): String = familyName
    override fun getFamilyName(): String = "Replace with block comment"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): PsiComment? {
        val comment = element.ancestorOrSelf<PsiComment>()
            ?.takeIf { it.elementType == EOL_COMMENT }
            ?: return null

        return generateSequence(comment) { it.prevComment }.last()
    }

    override fun invoke(project: Project, editor: Editor, ctx: PsiComment) {
        val firstLineComment = ctx
        val indent = (firstLineComment.prevSibling as? PsiWhiteSpace)
            ?.text
            ?.substringAfterLast('\n')
            .orEmpty()

        val lineComments = generateSequence(firstLineComment) { it.nextComment }.toList()

        val blockCommentText = if (lineComments.size == 1) {
            " ${firstLineComment.content} "
        } else {
            lineComments.joinToString(separator = "\n", prefix = "\n", postfix = "\n$indent") {
                "$indent${it.content}"
            }
        }

        for (lineComment in lineComments.drop(1)) {
            val prevSibling = lineComment.prevSibling
            if (prevSibling is PsiWhiteSpace) {
                prevSibling.delete()
            }
            lineComment.delete()
        }

        val newBlockComment = RsPsiFactory(project).createBlockComment(blockCommentText)
        firstLineComment.replace(newBlockComment)
    }

    private val PsiComment.content: String
        get() = text
            .drop(LINE_COMMENT_PREFIX_LEN)
            .replace("/*", "/ *")
            .replace("*/", "* /")
            .trim()

    private val PsiComment.prevComment: PsiComment?
        get() = (getPrevNonWhitespaceSibling() as? PsiComment)?.takeIf { it.elementType == EOL_COMMENT }

    private val PsiComment.nextComment: PsiComment?
        get() = (getNextNonWhitespaceSibling() as? PsiComment)?.takeIf { it.elementType == EOL_COMMENT }

    companion object {
        const val LINE_COMMENT_PREFIX_LEN: Int = 2 // the length of `//`
    }
}
