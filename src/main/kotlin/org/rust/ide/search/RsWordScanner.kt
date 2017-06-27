/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.psi.RS_COMMENTS
import org.rust.lang.core.psi.RsElementTypes.*

class RsWordScanner : DefaultWordsScanner(
    RsLexer(),
    TokenSet.create(IDENTIFIER),
    RS_COMMENTS,
    TokenSet.create(STRING_LITERAL)
)
