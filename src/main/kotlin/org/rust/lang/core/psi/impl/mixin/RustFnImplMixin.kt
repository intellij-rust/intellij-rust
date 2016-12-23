package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.psi.RustFnElement
import org.rust.lang.core.psi.RustInnerAttrElement
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.psi.queryAttributes
import org.rust.lang.core.stubs.RustFnStub
import org.rust.lang.core.stubs.RustNamedStub

abstract class RustFnImplMixin<StubT> : RustStubbedNamedElementImpl<StubT>,
                                        RustFnElement
    where StubT : RustFnStub, StubT: RustNamedStub, StubT : StubElement<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    final override val innerAttrList: List<RustInnerAttrElement>
        get() = block?.innerAttrList.orEmpty()

    override val isAbstract: Boolean get() = stub?.isAbstract ?: block == null
    override val isStatic: Boolean get() = stub?.isStatic ?: parameters?.selfArgument == null
    override val isTest: Boolean get() = stub?.isTest ?: queryAttributes.hasAtomAttribute("test")

}

