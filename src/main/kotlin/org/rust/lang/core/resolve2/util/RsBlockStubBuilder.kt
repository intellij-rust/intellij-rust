/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.util

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.psi.RS_ITEMS
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.tokenSetOf
import org.rust.lang.core.stubs.RsBlockStubType

/** This stub builder is not used to build real PSI stubs */
private class RsBlockStubBuilder : DefaultStubBuilder() {
    fun buildStubTreeFor(root: RsBlock): StubElement<RsBlock>? {
        val parentStub = PsiFileStubImpl(null)
        RsBlockStubBuildingWalkingVisitor(root.node, parentStub).buildStubTree()
        @Suppress("UNCHECKED_CAST")
        return parentStub.findChildStubByType(RsBlockStubType as IStubElementType<StubElement<RsBlock>, RsBlock>)
    }

    private inner class RsBlockStubBuildingWalkingVisitor(
        root: ASTNode,
        parentStub: StubElement<*>
    ) : StubBuildingWalkingVisitor(root, parentStub) {
        override fun createStub(parentStub: StubElement<*>, node: ASTNode): StubElement<*>? {
            val nodeType = node.elementType as? IStubElementType<*, *> ?: return null
            val parentType = (parentStub as? PsiFileStub)?.type ?: parentStub.stubType
            if (!shouldCreateStub(parentType, nodeType)) return null

            @Suppress("UNCHECKED_CAST")
            nodeType as IStubElementType<*, PsiElement>
            return nodeType.createStub(node.psi, parentStub)
        }
    }

    override fun skipChildProcessingWhenBuildingStubs(parent: ASTNode, node: ASTNode): Boolean =
        !shouldCreateStub(parent.elementType, node.elementType)

    private fun shouldCreateStub(parentType: IElementType, nodeType: IElementType): Boolean =
        when (nodeType) {
            BLOCK -> parentType is IStubFileElementType<*>
            MACRO_CALL -> parentType == BLOCK
            PATH, USE_GROUP, USE_SPECK, ALIAS, ENUM_BODY, ENUM_VARIANT -> true
            else -> parentType == BLOCK && nodeType in RS_ITEMS_AND_MACRO
        }

    companion object {
        private val RS_ITEMS_AND_MACRO: TokenSet = TokenSet.orSet(
            TokenSet.andNot(RS_ITEMS, tokenSetOf(IMPL_ITEM)),
            tokenSetOf(MACRO)
        )
    }
}

fun RsBlock.buildStub(): StubElement<RsBlock>? = RsBlockStubBuilder().buildStubTreeFor(this)
