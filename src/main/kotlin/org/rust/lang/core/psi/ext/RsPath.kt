package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.resolve.ref.RsPathReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsPathStub

//TODO: the name is awful
val RsPath.isCrateRelative: Boolean get() = stub?.isCrateRelative ?: (coloncolon != null)

private val RS_CODE_FRAGMENT_CONTEXT = Key.create<RsCompositeElement>("org.rust.lang.core.psi.CODE_FRAGMENT_FILE")

fun RsPath.setContext(ctx: RsCompositeElement) {
    putUserData(RS_CODE_FRAGMENT_CONTEXT, ctx)
}

abstract class RsPathImplMixin : RsStubbedElementImpl<RsPathStub>,
                                 RsPath {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RsPathStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsReference = RsPathReferenceImpl(this)

    override val referenceNameElement: PsiElement
        get() = checkNotNull(identifier ?: self ?: `super` ?: cself) {
            "Path must contain identifier: $this ${this.text} at ${this.containingFile.virtualFile.path}"
        }

    override val referenceName: String get() = stub?.referenceName ?: referenceNameElement.text

    override fun getContext(): PsiElement? = getUserData(RS_CODE_FRAGMENT_CONTEXT) ?: parent
}
