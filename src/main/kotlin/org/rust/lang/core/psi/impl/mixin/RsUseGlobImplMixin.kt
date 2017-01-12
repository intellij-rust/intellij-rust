package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsUseGlob
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.impl.RsStubbedElementImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve.ref.RsUseGlobReferenceImpl
import org.rust.lang.core.stubs.RsUseGlobStub

abstract class RsUseGlobImplMixin : RsStubbedElementImpl<RsUseGlobStub>, RsUseGlob {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsUseGlobStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsReference =
        RsUseGlobReferenceImpl(this)

    override val referenceNameElement: PsiElement
        get() = requireNotNull(identifier ?: self) {
            "Use glob must have an identifier: $this ${this.text} at ${this.containingFile.virtualFile.path}"
        }

    override val referenceName: String get() = stub?.referenceName ?: referenceNameElement.text

    override fun getParent(): PsiElement = parentByStub
}

val RsUseGlob.basePath: RsPath?
    get() = parentOfType<RsUseItem>()?.path

val RsUseGlob.isSelf: Boolean get() = referenceName == "self"
