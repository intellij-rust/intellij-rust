package org.rust.lang

import com.intellij.lang.Commenter

class RustCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "//"

    override fun getBlockCommentPrefix(): String = "/*"
    override fun getBlockCommentSuffix(): String = "*/"

    // for nested comments

    override fun getCommentedBlockCommentPrefix(): String = "/*"
    override fun getCommentedBlockCommentSuffix(): String = "*/"
}