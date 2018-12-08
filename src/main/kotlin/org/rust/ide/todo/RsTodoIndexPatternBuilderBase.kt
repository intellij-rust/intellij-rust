/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.todo

import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.psi.*

abstract class RsTodoIndexPatternBuilderBase : IndexPatternBuilder {

    override fun getIndexingLexer(file: PsiFile): Lexer? = if (file is RsFile) RsLexer() else null
    override fun getCommentTokenSet(file: PsiFile): TokenSet? = if (file is RsFile) RS_COMMENTS else null

    override fun getCommentStartDelta(tokenType: IElementType?): Int {
        return when (tokenType) {
            in RS_REGULAR_COMMENTS -> 2
            in RS_DOC_COMMENTS -> 3
            else -> 0
        }
    }

    override fun getCommentEndDelta(tokenType: IElementType?): Int = if (tokenType in RS_BLOCK_COMMENTS) 2 else 0
}
