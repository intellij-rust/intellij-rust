/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.todo

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.BaseFilterLexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.psi.RS_COMMENTS
import org.rust.lang.core.psi.RsElementTypes.EXCL
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER

class RsTodoIndexer : LexerBasedTodoIndexer() {
    override fun getVersion(): Int = 2
    override fun createLexer(consumer: OccurrenceConsumer): Lexer = RsFilterLexer(consumer)
}

private class RsFilterLexer(consumer: OccurrenceConsumer) : BaseFilterLexer(RsLexer(), consumer) {

    override fun advance() {
        when (myDelegate.tokenType) {
            in RS_COMMENTS -> {
                scanWordsInToken(UsageSearchContext.IN_COMMENTS.toInt(), false, false)
                advanceTodoItemCountsInToken()
            }
            IDENTIFIER -> {
                addOccurrenceInToken(UsageSearchContext.IN_CODE.toInt())
                if (myDelegate.tokenText == "todo") {
                    if (nextToken() == EXCL) {
                        advanceTodoItemCountsInToken()
                    }
                }
            }
        }

        myDelegate.advance()
    }

    private fun nextToken(): IElementType? {
        val position = myDelegate.currentPosition
        return try {
            myDelegate.advance()
            myDelegate.tokenType
        } finally {
            myDelegate.restore(position)
        }
    }
}
