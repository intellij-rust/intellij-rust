package org.rust.lang.core.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.DefaultASTFactory
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILeafElementType
import org.rust.ide.utils.service
import org.rust.lang.RustLanguage

private val defaultASTFactory by lazy { service<DefaultASTFactory>() }

open class RustTokenType(debugName: String) : IElementType(debugName, RustLanguage), ILeafElementType {
    override fun createLeafNode(leafText: CharSequence): ASTNode = LeafPsiElement(this, leafText)
}

class RustCommentTokenType(debugName: String) : RustTokenType(debugName) {
    override fun createLeafNode(leafText: CharSequence): ASTNode = defaultASTFactory.createComment(this, leafText)
}

class RustKeywordTokenType(debugName: String) : RustTokenType(debugName)

class RustOperatorTokenType(debugName: String) : RustTokenType(debugName)

class RustLiteralTokenType(
    debugName: String,
    private val implConstructor: (IElementType, CharSequence) -> RustLiteral
) : RustTokenType(debugName) {
    override fun createLeafNode(leafText: CharSequence): ASTNode = implConstructor(this, leafText)
}
