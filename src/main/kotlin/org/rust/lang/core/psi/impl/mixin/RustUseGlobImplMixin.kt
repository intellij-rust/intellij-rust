package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustPathElement
import org.rust.lang.core.psi.RustUseGlobElement
import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.impl.RustStubbedElementImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.resolve.ref.RustUseGlobReferenceImpl
import org.rust.lang.core.stubs.RustUseGlobElementStub

abstract class RustUseGlobImplMixin : RustStubbedElementImpl<RustUseGlobElementStub>, RustUseGlobElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustUseGlobElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RustReference =
        RustUseGlobReferenceImpl(this)

    override val referenceNameElement: PsiElement
        get() = requireNotNull(identifier ?: self) {
            "Use glob must have an identifier: $this ${this.text} at ${this.containingFile.virtualFile.path}"
        }

    override val referenceName: String get() = stub?.referenceName ?: referenceNameElement.text
}

val RustUseGlobElement.basePath: RustPathElement?
    get() = parentOfType<RustUseItemElement>()?.path

val RustUseGlobElement.isSelf: Boolean get() = referenceName == "self"
