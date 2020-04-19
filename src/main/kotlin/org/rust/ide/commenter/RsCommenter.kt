/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.commenter

import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.lang.Commenter
import com.intellij.psi.PsiComment
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.parser.RustParserDefinition.Companion.BLOCK_COMMENT

class RsCommenter : Commenter, CodeDocumentationAwareCommenter {
    // act like there are no doc or line comments, these are handled separately
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

    // for nested comments

    override fun getCommentedBlockCommentPrefix(): String = "*//*"
    override fun getCommentedBlockCommentSuffix(): String = "*//*"
}
