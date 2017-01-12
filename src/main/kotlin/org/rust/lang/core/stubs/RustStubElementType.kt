package org.rust.lang.core.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IStubFileElementType
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RsCompositeElement

abstract class RustStubElementType<StubT : StubElement<*>, PsiT : RsCompositeElement>(
    debugName: String
) : IStubElementType<StubT, PsiT>(debugName, RustLanguage) {

    final override fun getExternalId(): String = "rust.${super.toString()}"

    protected fun createStubIfParentIsStub(node: ASTNode): Boolean {
        val parent = node.treeParent
        val parentType = parent.elementType
        return (parentType is IStubElementType<*, *> && parentType.shouldCreateStub(parent)) ||
            parentType is IStubFileElementType<*>
    }
}
