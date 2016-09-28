package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustFnElement
import org.rust.lang.core.psi.RustInnerAttrElement
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.psi.queryAttributes
import org.rust.lang.core.stubs.RustFnElementStub

abstract class RustFnImplMixin<StubT : RustFnElementStub<*>> : RustStubbedNamedElementImpl<StubT>,
                                                               RustFnElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    final override val innerAttrList: List<RustInnerAttrElement>
        get() = block?.innerAttrList.orEmpty()

    override val isAbstract: Boolean get() = stub?.attributes?.isAbstract ?: block == null
    override val isStatic: Boolean get() = stub?.attributes?.isStatic ?: parameters?.selfArgument == null
    override val isTest: Boolean get() = stub?.attributes?.isTest ?: queryAttributes.hasAtomAttribute("test")

}

