package org.rust.lang.search

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.lexer.RustLexer
import org.rust.lang.core.lexer.RustTokenElementTypes

class RustWordScanner : DefaultWordsScanner(RustLexer(),
    TokenSet.create(RustTokenElementTypes.IDENTIFIER),
    RustTokenElementTypes.COMMENTS_TOKEN_SET,
    TokenSet.create(RustTokenElementTypes.STRING_LITERAL))
