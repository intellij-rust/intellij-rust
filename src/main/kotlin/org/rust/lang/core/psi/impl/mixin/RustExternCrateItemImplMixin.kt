package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustExternCrateItemElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustItemElementImpl
import org.rust.lang.core.resolve.ref.RustExternCrateReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.stubs.RustItemStub

abstract class RustExternCrateItemImplMixin : RustItemElementImpl, RustExternCrateItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RustReference = RustExternCrateReferenceImpl(this)

    override val boundElements: Collection<RustNamedElement>
        get() = listOfNotNull(alias ?: this)
}
