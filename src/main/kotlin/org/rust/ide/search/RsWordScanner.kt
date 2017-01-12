package org.rust.ide.search

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.lexer.RustLexer
import org.rust.lang.core.psi.RsTokenElementTypes

class RsWordScanner : DefaultWordsScanner(
    RustLexer(),
    TokenSet.create(RsTokenElementTypes.IDENTIFIER),
    RsTokenElementTypes.COMMENTS_TOKEN_SET,
    TokenSet.create(RsTokenElementTypes.STRING_LITERAL)
)
