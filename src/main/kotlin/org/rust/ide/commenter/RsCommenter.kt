/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.commenter

import com.intellij.codeInsight.generation.IndentedCommenter

class RsCommenter : IndentedCommenter {
    override fun getLineCommentPrefix(): String = "// "

    override fun getBlockCommentPrefix(): String = "/*"
    override fun getBlockCommentSuffix(): String = "*/"

    // for nested comments

    override fun getCommentedBlockCommentPrefix(): String = "*//*"
    override fun getCommentedBlockCommentSuffix(): String = "*//*"

    override fun forceIndentedLineComment(): Boolean = true

}
