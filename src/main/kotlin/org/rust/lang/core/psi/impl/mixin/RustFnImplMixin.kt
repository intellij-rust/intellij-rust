package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustFnElement
import org.rust.lang.core.psi.RustInnerAttrElement
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub

abstract class RustFnImplMixin<StubT: RustNamedElementStub<*>> : RustStubbedNamedElementImpl<StubT>
                                                               , RustFnElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    final override val innerAttrList: List<RustInnerAttrElement>
        get() = block?.innerAttrList.orEmpty()

}

