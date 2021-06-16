/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.lexer

import com.intellij.lexer.LayeredLexer
import org.rust.lang.core.lexer.RsEscapesLexer.Companion.ESCAPABLE_LITERALS_TOKEN_SET

class RsHighlightingLexer : LayeredLexer(RsLexer()) {
    init {
        ESCAPABLE_LITERALS_TOKEN_SET.types.forEach {
            registerLayer(RsEscapesLexer.of(it), it)
        }
    }
}
