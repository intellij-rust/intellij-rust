/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.lexer

import com.intellij.lexer.LayeredLexer
import org.rust.lang.core.lexer.RustEscapesLexer.Companion.ESCAPABLE_LITERALS_TOKEN_SET
import org.rust.lang.core.psi.RS_DOC_COMMENTS
import org.rust.lang.doc.lexer.RsDocHighlightingLexer
import org.rust.lang.doc.psi.RsDocKind

class RsHighlightingLexer : LayeredLexer(RsLexer()) {
    init {
        ESCAPABLE_LITERALS_TOKEN_SET.types.forEach {
            registerLayer(RustEscapesLexer.of(it), it)
        }

        RS_DOC_COMMENTS.types.forEach {
            registerLayer(RsDocHighlightingLexer(RsDocKind.of(it)), it)
        }
    }
}
