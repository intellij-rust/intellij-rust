/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.parser.RustParserDefinition
import org.rust.lang.core.psi.RS_ALL_STRING_LITERALS
import org.rust.lang.core.psi.RS_COMMENTS
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER

class RsWordScanner : DefaultWordsScanner(
    RsLexer(),
    TokenSet.create(IDENTIFIER),
    RS_COMMENTS,
    RS_ALL_STRING_LITERALS
) {
    init {
        // This actually means that it's possible to do language injections into Rust string literals
        setMayHaveFileRefsInLiterals(true)
    }

    override fun getVersion(): Int = RustParserDefinition.LEXER_VERSION + VERSION

    companion object {
        private const val VERSION = 1
    }
}
