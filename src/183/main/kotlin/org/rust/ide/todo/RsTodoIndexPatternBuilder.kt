/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.todo

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_EOL_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.RS_BLOCK_COMMENTS

class RsTodoIndexPatternBuilder : RsTodoIndexPatternBuilderBase() {

    override fun getCharsAllowedInContinuationPrefix(tokenType: IElementType): String {
        return when (tokenType) {
            INNER_EOL_DOC_COMMENT -> "/!"
            OUTER_EOL_DOC_COMMENT -> "/"
            in RS_BLOCK_COMMENTS -> "*"
            else -> ""
        }
    }
}
