package org.rust.lang.core.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.DefaultASTFactory
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILeafElementType
import org.rust.ide.utils.service
import org.rust.lang.RsLanguage

private val defaultASTFactory by lazy { service<DefaultASTFactory>() }

open class RsTokenType(debugName: String) : IElementType(debugName, RsLanguage), ILeafElementType {
    override fun createLeafNode(leafText: CharSequence): ASTNode = LeafPsiElement(this, leafText)
}

class RsCommentTokenType(debugName: String) : RsTokenType(debugName) {
    override fun createLeafNode(leafText: CharSequence): ASTNode = defaultASTFactory.createComment(this, leafText)
}

class RsKeywordTokenType(debugName: String) : RsTokenType(debugName)

class RsOperatorTokenType(debugName: String) : RsTokenType(debugName)

class RsLiteralTokenType(
    debugName: String,
    private val implConstructor: (IElementType, CharSequence) -> RsLiteral
) : RsTokenType(debugName) {
    override fun createLeafNode(leafText: CharSequence): ASTNode = implConstructor(this, leafText)
}
