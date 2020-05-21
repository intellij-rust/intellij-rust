/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.commenter

import com.intellij.codeInsight.generation.CommenterDataHolder
import com.intellij.codeInsight.generation.SelfManagingCommenter
import com.intellij.codeInsight.generation.SelfManagingCommenterUtil
import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.lang.Commenter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharArrayUtil
import org.rust.lang.core.parser.RustParserDefinition.Companion.BLOCK_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.EOL_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_EOL_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.RS_EOL_COMMENTS
import org.rust.lang.doc.psi.RsDocKind

data class CommentHolder(val file: PsiFile) : CommenterDataHolder()

class RsCommenter : Commenter, CodeDocumentationAwareCommenter, SelfManagingCommenter<CommentHolder> {
    // act like there are no doc comments, these are handled in `RsEnterInLineCommentHandler`
    override fun isDocumentationComment(element: PsiComment?) = false
    override fun getDocumentationCommentTokenType(): IElementType? = null
    override fun getDocumentationCommentLinePrefix(): String? = null
    override fun getDocumentationCommentPrefix(): String? = null
    override fun getDocumentationCommentSuffix(): String? = null

    override fun getLineCommentTokenType(): IElementType? = null
    override fun getBlockCommentTokenType(): IElementType = BLOCK_COMMENT

    override fun getLineCommentPrefix(): String = "//"

    override fun getBlockCommentPrefix(): String = "/*"
    override fun getBlockCommentSuffix(): String = "*/"

    // unused because we implement SelfManagingCommenter
    override fun getCommentedBlockCommentPrefix(): String = "*//*"
    override fun getCommentedBlockCommentSuffix(): String = "*//*"

    override fun getBlockCommentPrefix(selectionStart: Int, document: Document, data: CommentHolder): String? =
        blockCommentPrefix

    override fun getBlockCommentSuffix(selectionEnd: Int, document: Document, data: CommentHolder): String? =
        blockCommentSuffix

    override fun getBlockCommentRange(
        selectionStart: Int,
        selectionEnd: Int,
        document: Document,
        data: CommentHolder
    ): TextRange? =
        SelfManagingCommenterUtil.getBlockCommentRange(selectionStart, selectionEnd, document, blockCommentPrefix, blockCommentSuffix)

    override fun insertBlockComment(
        startOffset: Int,
        endOffset: Int,
        document: Document,
        data: CommentHolder?
    ): TextRange {
        val sequence = document.charsSequence
        val start = CharArrayUtil.shiftForward(sequence, startOffset, " \t\n")
        val end = CharArrayUtil.shiftBackward(sequence, endOffset - 1, " \t\n")
        return SelfManagingCommenterUtil.insertBlockComment(start, end + 1, document, blockCommentPrefix, blockCommentSuffix)
    }

    override fun uncommentBlockComment(
        startOffset: Int,
        endOffset: Int,
        document: Document,
        data: CommentHolder?
    ) =
        SelfManagingCommenterUtil.uncommentBlockComment(startOffset, endOffset, document, blockCommentPrefix, blockCommentSuffix)

    override fun isLineCommented(line: Int, offset: Int, document: Document, data: CommentHolder): Boolean {
        return getStartLineComment(line, document, data.file)?.isEolComment ?: false
    }

    override fun commentLine(line: Int, offset: Int, document: Document, data: CommentHolder) {
        document.insertString(offset, "// ")
    }

    override fun uncommentLine(line: Int, offset: Int, document: Document, data: CommentHolder) {
        val element = getStartLineComment(line, document, data.file) ?: return
        val prefix = getEolCommentPrefix(element.tokenType) ?: return
        val prefixWithSpace = "$prefix "
        val length = if (element.text.startsWith(prefixWithSpace)) {
            prefixWithSpace.length
        } else {
            prefix.length
        }
        document.deleteString(offset, offset + length)
    }

    override fun getCommentPrefix(line: Int, document: Document, data: CommentHolder): String? {
        val token = getStartLineComment(line, document, data.file)?.tokenType ?: return null
        return getEolCommentPrefix(token)
    }

    override fun createBlockCommentingState(
        selectionStart: Int,
        selectionEnd: Int,
        document: Document,
        file: PsiFile
    ): CommentHolder? =
        CommentHolder(file)

    override fun createLineCommentingState(
        startLine: Int,
        endLine: Int,
        document: Document,
        file: PsiFile
    ): CommentHolder? =
        CommentHolder(file)
}

private fun getStartLineComment(line: Int, document: Document, file: PsiFile): PsiComment? {
    val offset = document.getLineStartOffset(line)
    val chars = document.charsSequence
    return file.findElementAt(CharArrayUtil.shiftForward(chars, offset, " \t")) as? PsiComment
}

private val PsiComment.isEolComment: Boolean
    get() = this.tokenType in RS_EOL_COMMENTS

private fun getEolCommentPrefix(tokenType: IElementType): String? {
    return when (tokenType) {
        EOL_COMMENT -> "//"
        INNER_EOL_DOC_COMMENT -> RsDocKind.InnerEol.prefix
        OUTER_EOL_DOC_COMMENT -> RsDocKind.OuterEol.prefix
        else -> null
    }
}
